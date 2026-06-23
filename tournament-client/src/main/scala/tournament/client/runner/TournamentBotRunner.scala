package tournament.client.runner

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Sink
import tournament.client.api.TournamentApiClient
import tournament.client.bot.BotMoveAdapter
import tournament.client.model._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Orchestrates tournament participation for a bot.
  * Handles auth, join, stream handling, move computation, and move submission.
  * Includes retry/logging behavior and deduplication.
  */
class TournamentBotRunner(
  baseUrl: String,
  botName: String,
  botType: String = "stockfish"
)(implicit system: ActorSystem[?], ec: ExecutionContext) {
  
  private val apiClient = TournamentApiClient(baseUrl)
  private val botAdapter = BotMoveAdapter(botType)
  
  // Deduplication: track (gameId + fen + turn) to avoid duplicate moves
  private var processedStates: Set[String] = Set.empty
  
  // Track assigned color for each game
  private var gameColors: Map[String, String] = Map.empty
  
  // Track active games
  private var activeGames: Set[String] = Set.empty
  
  /** Register the bot and obtain a JWT token */
  def register(): Future[RegisterResponse] = {
    println(s"[INFO] Registering bot '$botName' with tournament server at $baseUrl")
    
    val request = RegisterRequest(name = botName, isBot = true)
    
    apiClient.register(request).map { response =>
      apiClient.setToken(response.token)
      println(s"[INFO] Bot registered successfully. ID: ${response.id}")
      response
    }.recover { case error =>
      println(s"[ERROR] Failed to register bot: ${error.getMessage}")
      throw error
    }
  }
  
  /** Join a tournament by ID */
  def joinTournament(tournamentId: String): Future[TournamentDetail] = {
    println(s"[INFO] Joining tournament $tournamentId")
    
    apiClient.joinTournament(tournamentId).map { tournament =>
      println(s"[INFO] Successfully joined tournament '${tournament.fullName}'")
      tournament
    }.recover { case error =>
      println(s"[ERROR] Failed to join tournament: ${error.getMessage}")
      throw error
    }
  }
  
  /** Start participating in a tournament: open stream and handle events */
  def participate(tournamentId: String): Future[Unit] = {
    println(s"[INFO] Starting participation in tournament $tournamentId")
    
    // Open tournament stream
    val streamSource = apiClient.openTournamentStream(tournamentId)
    
    // Process tournament events
    streamSource
      .runWith(Sink.foreach { event =>
        handleTournamentEvent(tournamentId, event)
      })
      .map { _ =>
        println(s"[INFO] Tournament stream ended for $tournamentId")
      }
      .recover { case error =>
        println(s"[ERROR] Tournament stream error: ${error.getMessage}")
        throw error
      }
  }
  
  /** Handle a tournament event */
  private def handleTournamentEvent(tournamentId: String, event: TournamentEvent): Unit = {
    event.`type` match {
      case "heartbeat" =>
        // Ignore heartbeat events (keep-alive only)
        ()
        
      case "tournamentStarted" =>
        println(s"[INFO] Tournament $tournamentId started")
        
      case "roundStarted" =>
        println(s"[INFO] Round ${event.round.getOrElse(0)} started in tournament $tournamentId")
        
      case "gameStart" =>
        event.gameId.foreach { gameId =>
          event.color.foreach { color =>
            handleGameStart(tournamentId, gameId, event.round.getOrElse(0), color)
          }
        }
        
      case "roundFinished" =>
        println(s"[INFO] Round ${event.round.getOrElse(0)} finished in tournament $tournamentId")
        // Clear processed states for this round
        processedStates = Set.empty
        
      case "tournamentFinished" =>
        println(s"[INFO] Tournament $tournamentId finished")
        activeGames = Set.empty
        
      case _ =>
        println(s"[WARN] Unknown tournament event type: ${event.`type`}")
    }
  }
  
  /** Handle a game start event */
  private def handleGameStart(tournamentId: String, gameId: String, round: Int, color: String): Unit = {
    println(s"[INFO] Game $gameId started in round $round")
    println(s"[INFO] Assigned color: $color")
    
    // Store assigned color for this game
    gameColors = gameColors + (gameId -> color)
    activeGames = activeGames + gameId
    
    // Open game stream
    openGameStream(tournamentId, gameId)
  }
  
  /** Open and handle game event stream */
  private def openGameStream(tournamentId: String, gameId: String): Unit = {
    println(s"[INFO] Opening game stream for $gameId")
    
    val streamSource = apiClient.openGameStream(tournamentId, gameId)
    
    streamSource
      .runWith(Sink.foreach { event =>
        handleGameEvent(tournamentId, gameId, event)
      })
      .onComplete {
        case Success(_) =>
          println(s"[INFO] Game stream ended for $gameId")
          activeGames = activeGames - gameId
          gameColors = gameColors - gameId
        case Failure(error) =>
          println(s"[ERROR] Game stream error for $gameId: ${error.getMessage}")
          activeGames = activeGames - gameId
          gameColors = gameColors - gameId
      }
  }
  
  /** Handle a game event */
  private def handleGameEvent(tournamentId: String, gameId: String, event: GameEvent): Unit = {
    event.`type` match {
      case "heartbeat" =>
        // Ignore heartbeat events
        ()
        
      case "gameState" =>
        event.fen.foreach { fen =>
          event.turn.foreach { turn =>
            handleGameState(tournamentId, gameId, fen, turn)
          }
        }
        
      case "move" =>
        event.uci.foreach { uci =>
          event.fen.foreach { fen =>
            event.turn.foreach { turn =>
              handleMoveEvent(tournamentId, gameId, uci, fen, turn)
            }
          }
        }
        
      case "gameEnd" =>
        event.winner.foreach { winner =>
          event.status.foreach { status =>
            handleGameEnd(tournamentId, gameId, winner, status)
          }
        }
        
      case _ =>
        println(s"[WARN] Unknown game event type: ${event.`type`}")
    }
  }
  
  /** Handle a game state event */
  private def handleGameState(tournamentId: String, gameId: String, fen: String, turn: String): Unit = {
    // Deduplication check
    val stateKey = s"$gameId:$fen:$turn"
    if (processedStates.contains(stateKey)) {
      println(s"[DEBUG] Duplicate state ignored for $gameId")
      return
    }
    processedStates = processedStates + stateKey
    
    // Get assigned color for this game
    val assignedColor = gameColors.get(gameId)
    if (assignedColor.isEmpty) {
      println(s"[WARN] No assigned color for game $gameId, skipping move")
      return
    }
    
    // Check if it's the bot's turn
    if (turn != assignedColor.get) {
      println(s"[DEBUG] Not bot's turn in $gameId: bot is ${assignedColor.get}, turn is $turn")
      return
    }
    
    println(s"[INFO] Bot's turn in $gameId (FEN: ${fen.take(20)}...)")
    
    // Compute and submit move
    computeAndSubmitMove(tournamentId, gameId, fen, turn, assignedColor.get)
  }
  
  /** Handle a move event (opponent's move) */
  private def handleMoveEvent(tournamentId: String, gameId: String, uci: String, fen: String, turn: String): Unit = {
    println(s"[INFO] Move played in $gameId: $uci")
    
    // Deduplication check
    val stateKey = s"$gameId:$fen:$turn"
    if (processedStates.contains(stateKey)) {
      println(s"[DEBUG] Duplicate state ignored for $gameId")
      return
    }
    processedStates = processedStates + stateKey
    
    // Get assigned color for this game
    val assignedColor = gameColors.get(gameId)
    if (assignedColor.isEmpty) {
      println(s"[WARN] No assigned color for game $gameId, skipping move")
      return
    }
    
    // Check if it's the bot's turn
    if (turn != assignedColor.get) {
      println(s"[DEBUG] Not bot's turn in $gameId: bot is ${assignedColor.get}, turn is $turn")
      return
    }
    
    println(s"[INFO] Bot's turn in $gameId after opponent move")
    
    // Compute and submit move
    computeAndSubmitMove(tournamentId, gameId, fen, turn, assignedColor.get)
  }
  
  /** Handle a game end event */
  private def handleGameEnd(tournamentId: String, gameId: String, winner: String, status: String): Unit = {
    println(s"[INFO] Game $gameId ended: $winner ($status)")
    activeGames = activeGames - gameId
    gameColors = gameColors - gameId
  }
  
  /** Compute a move and submit it to the tournament server */
  private def computeAndSubmitMove(
    tournamentId: String,
    gameId: String,
    fen: String,
    turn: String,
    assignedColor: String
  ): Unit = {
    val startTime = System.currentTimeMillis()
    
    // Compute move using bot adapter
    botAdapter.computeMove(fen, turn, assignedColor, gameId) match {
      case Right(uciMove) =>
        val computeTime = System.currentTimeMillis() - startTime
        println(s"[INFO] Computed move $uciMove for $gameId in ${computeTime}ms")
        
        // Submit move to tournament server
        submitMoveWithRetry(tournamentId, gameId, uciMove)
        
      case Left(error) =>
        println(s"[ERROR] Failed to compute move for $gameId: $error")
    }
  }
  
  /** Submit a move with retry logic */
  private def submitMoveWithRetry(
    tournamentId: String,
    gameId: String,
    uci: String,
    maxRetries: Int = 3,
    retryDelay: FiniteDuration = 1.second
  ): Unit = {
    def attempt(retryCount: Int): Future[Unit] = {
      apiClient.submitMove(tournamentId, gameId, uci).map { _ =>
        println(s"[INFO] Successfully submitted move $uci for $gameId")
      }.recoverWith { case error =>
        if (retryCount < maxRetries) {
          println(s"[WARN] Failed to submit move $uci for $gameId (attempt ${retryCount + 1}/$maxRetries): ${error.getMessage}")
          Thread.sleep(retryDelay.toMillis)
          attempt(retryCount + 1)
        } else {
          println(s"[ERROR] Failed to submit move $uci for $gameId after $maxRetries attempts: ${error.getMessage}")
          Future.failed(error)
        }
      }
    }
    
    attempt(0)
  }
  
  /** Get current active games */
  def getActiveGames: Set[String] = activeGames
  
  /** Shutdown the bot runner */
  def shutdown(): Future[Unit] = {
    println(s"[INFO] Shutting down bot runner for $botName")
    apiClient.shutdown()
  }
}

object TournamentBotRunner {
  /** Create a new tournament bot runner */
  def apply(
    baseUrl: String,
    botName: String,
    botType: String = "stockfish"
  )(implicit system: ActorSystem[?], ec: ExecutionContext): TournamentBotRunner = {
    new TournamentBotRunner(baseUrl, botName, botType)
  }
  
  /** Run a bot in a tournament (convenience method) */
  def runInTournament(
    baseUrl: String,
    botName: String,
    tournamentId: String,
    botType: String = "stockfish"
  ): Future[Unit] = {
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-bot-runner")
    implicit val ec: ExecutionContext = system.executionContext
    
    val runner = TournamentBotRunner(baseUrl, botName, botType)
    
    for {
      _ <- runner.register()
      _ <- runner.joinTournament(tournamentId)
      _ <- runner.participate(tournamentId)
    } yield {
      runner.shutdown()
      system.terminate()
    }
  }
}
