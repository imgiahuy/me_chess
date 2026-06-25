package api

import usecase.{CreateGameUseCase, MakeMoveUseCase, GetGameUseCase}
import controller.GameControllerInterface
import model.{PositionState, Color}
import repository.GameRepository
import service.{GameService, BotService}
import kafka.{KafkaGameEventService, GameEvent}
import engine.{UciBotService, EngineManager}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

class GameSessionController(
                             createGameUseCase: CreateGameUseCase,
                             makeMoveUseCase: MakeMoveUseCase,
                             getGameUseCase: GetGameUseCase,
                             controller: GameControllerInterface,
                             repo: GameRepository,
                             kafkaService: Option[KafkaGameEventService] = None,
                             playerServiceClient: Option[PlayerServiceClient] = None,
                             authClient: Option[auth.AuthClient] = None
                           )(implicit ec: ExecutionContext) {

  // Initialize UCI bot service for Stockfish integration
  private val uciBotService = UciBotService()
  uciBotService.initialize()

  /** Create a new game session and return its ID */
  def createGame(): String = {
    createGameUseCase.create()
  }

  /** Create a new game session with player names and return its ID */
  def createGame(whitePlayer: String, blackPlayer: String): String = {
    createGameUseCase.create(whitePlayer, blackPlayer)
  }

  /** Create a new game session with player names and time control */
  def createGameWithTimeControl(whitePlayer: String, blackPlayer: String, timeControl: model.TimeControl): String = {
    // Detect if players are bots (names starting with "Bot (")
    val isWhiteBot = whitePlayer.startsWith("Bot (")
    val isBlackBot = blackPlayer.startsWith("Bot (")

    // For human players, get or create in player-service
    val whitePlayerIdFuture = if (isWhiteBot) Future.successful(None) else {
      playerServiceClient match {
        case Some(client) => client.getOrCreatePlayer(whitePlayer).map(Some(_))
        case None => Future.successful(None)
      }
    }
    val blackPlayerIdFuture = if (isBlackBot) Future.successful(None) else {
      playerServiceClient match {
        case Some(client) => client.getOrCreatePlayer(blackPlayer).map(Some(_))
        case None => Future.successful(None)
      }
    }

    // Create game after player IDs are resolved
    val gameIdFuture = for {
      whiteIdOpt <- whitePlayerIdFuture
      blackIdOpt <- blackPlayerIdFuture
    } yield {
      val gameId = createGameUseCase.createWithTimeControl(whitePlayer, blackPlayer, timeControl)
      val initialState = repo.getGame(gameId).get

      // Store player IDs in the game state metadata (for future use)
      // Note: PositionState doesn't have player ID fields yet, so we log for now
      whiteIdOpt.foreach(id => println(s"[PlayerService] White player ID: $id"))
      blackIdOpt.foreach(id => println(s"[PlayerService] Black player ID: $id"))

      // Publish GameCreated event to Kafka
      kafkaService.foreach { service =>
        service.publishGameCreated(gameId, whitePlayer, blackPlayer, initialState)
          .recover { case ex => println(s"[Kafka] Failed to publish game created: ${ex.getMessage}") }
      }

      gameId
    }

    // Block for synchronous API (this is called from a non-async route)
    import scala.concurrent.Await
    import scala.concurrent.duration._
    Await.result(gameIdFuture, 10.seconds)
  }

  /** Get a game by ID */
  def getGame(gameId: String): Option[PositionState] =
    getGameUseCase.getGame(gameId)

  /** Apply a move using gameId */
  def makeMove(gameId: String, input: String): Either[String, PositionState] = {
    makeMoveUseCase.makeMove(gameId, input)
  }

  /** Player resigns from game */
  def resign(gameId: String, color: Color): PositionState = {
    repo.getGame(gameId) match {
      case Some(state) =>
        val newState = GameService.resign(state, color)
        repo.updateGame(gameId, newState)

        // Publish PlayerResigned and GameEnded events to Kafka
        kafkaService.foreach { service =>
          val colorStr = color.toString.toLowerCase
          service.publishPlayerResigned(gameId, colorStr)
            .recover { case ex => println(s"[Kafka] Failed to publish resignation: ${ex.getMessage}") }

          service.publishGameEnded(gameId, newState)
            .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
        }

        newState
      case None =>
        throw new Exception("Game not found")
    }
  }

  /** Play a bot move */
  def playBotMove(gameId: String, botType: String): Either[String, PositionState] = {
    for {
      state <- repo.getGame(gameId).toRight("Game not found")
      newState <- {
        // Use BotService for all bot types (including Stockfish)
        val bot = BotService.createBot(botType)
        BotService.playBotMove(bot, state)
      }
    } yield {
      repo.updateGame(gameId, newState)

      // Publish bot move event to Kafka
      kafkaService.foreach { service =>
        val lastMove = newState.moveHistory.lastOption
        val moveUci = lastMove.map(formatter.UciFormatter.moveToUci).getOrElse("")
        val moveNumber = newState.moveHistory.length

        service.publishMoveMade(gameId, newState, moveUci, moveNumber)
          .recover { case ex => println(s"[Kafka] Failed to publish bot move: ${ex.getMessage}") }

        // If game ended, publish GameEnded event
        if (newState.gameResult != model.Ongoing) {
          service.publishGameEnded(gameId, newState)
            .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
        }
      }

      newState
    }
  }

  /** Get available bot types */
  def getAvailableBots(): List[String] = {
    BotService.availableBots
  }

  /** Get available bot types with metadata */
  def getAvailableBotInfos(): List[model.BotInfo] = {
    model.BotFactory.availableBotInfo ++ List(
      model.BotInfo("stockfish", "Stockfish (Expert)", "Expert", "Stockfish chess engine at maximum strength (depth 15). World-class play."),
      model.BotInfo("stockfish-easy", "Stockfish (Easy)", "Medium", "Stockfish chess engine at reduced strength (depth 10). Good for intermediate players."),
      model.BotInfo("stockfish-medium", "Stockfish (Medium)", "Hard", "Stockfish chess engine at medium strength (depth 12). Challenging for advanced players.")
    )
  }

  /** Pause a game */
  def pauseGame(gameId: String): Either[String, PositionState] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        GameService.pauseGame(state) match {
          case Right(newState) =>
            repo.updateGame(gameId, newState)
            Right(newState)
          case Left(err) => Left(err)
        }
      case None => Left("Game not found")
    }
  }

  /** Resume a game */
  def resumeGame(gameId: String): Either[String, PositionState] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        GameService.resumeGame(state) match {
          case Right(newState) =>
            repo.updateGame(gameId, newState)
            Right(newState)
          case Left(err) => Left(err)
        }
      case None => Left("Game not found")
    }
  }

  /** Delete a game session */
  def deleteGame(gameId: String): Boolean =
    repo.deleteGame(gameId)

  /** List all active games */
  def listGames(): List[String] =
    getGameUseCase.listGames()

  /** Get lightweight summaries of all games */
  def getGameSummaries(): List[(String, String, Int, Boolean)] =
    getGameUseCase.getGameSummaries()

  /** Save a specific game to the database */
  def saveGame(gameId: String): Either[String, Unit] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        try {
          repo.updateGame(gameId, state)
          Right(())
        } catch {
          case e: Exception => Left(s"Failed to save game: ${e.getMessage}")
        }
      case None =>
        Left("Game not found")
    }
  }

  /** Load a game from the database by ID and create a new session for it */
  def loadGame(gameId: String): Either[String, String] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        state.id match {
          case Some(existingId) => Right(existingId)
          case None =>
            try {
              Right(repo.createGame(state))
            } catch {
              case e: Exception => Left(s"Failed to load game: ${e.getMessage}")
            }
        }
      case None =>
        Left("Game not found")
    }
  }

  /** Load the latest game from the database and create a new session for it */
  def loadLatestGame(): Either[String, String] = {
    repo.loadLatestGame()
  }

  /** Check if a game is over */
  def isGameOver(gameId: String): Either[String, Boolean] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(GameService.isGameOver(state))
      case None => Left("Game not found")
    }
  }

  /** Update a game state by ID */
  def updateGame(gameId: String, newState: PositionState): Unit = repo.updateGame(gameId, newState)

  /** Get the winner of a game, if any */
  def getWinner(gameId: String): Either[String, Option[String]] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(GameService.winner(state).map(_.toString))
      case None => Left("Game not found")
    }
  }
  
  /** Get the current turn for a game */
  def getCurrentTurn(gameId: String): Either[String, String] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(state.turn.toString)
      case None => Left("Game not found")
    }
  }
  
  /** Get move count for a game */
  def getMoveCount(gameId: String): Either[String, Int] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(state.moveHistory.length)
      case None => Left("Game not found")
    }
  }
  
  /** Get game statistics */
  def getGameStats(gameId: String): Either[String, (Int, String, Boolean, Option[String])] = {
    repo.getGame(gameId) match {
      case Some(state) => 
        Right((state.moveHistory.length, state.turn.toString, GameService.isGameOver(state), GameService.winner(state).map(_.toString)))
      case None => 
        Left("Game not found")
    }
  }
  
  /** Export game to PGN format */
  def exportToPgn(gameId: String, event: String, site: String): String = {
    repo.getGame(gameId) match {
      case Some(state) =>
        controller.exportToPgn(state, event, site, s"game_${gameId}.pgn")
        // Return the PGN content directly for the API response
        service.GameService.exportToPgn(state, event, site)
      case None =>
        throw new Exception("Game not found")
    }
  }

  /** Check and apply timeout if a player's time has expired */
  def checkAndApplyTimeout(gameId: String): Either[String, PositionState] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        if (state.gameResult != model.Ongoing) {
          // Game already over, just return current state
          Right(state)
        } else if (state.isPaused) {
          // Don't check timeout while paused — clocks are frozen
          Right(state)
        } else {
          // Check if current player's time has expired using real-time calculation
          val currentPlayer = state.turn
          val isTimeExpired = currentPlayer match {
            case model.White => state.whiteTime.exists(_.isTimeOver)
            case model.Black => state.blackTime.exists(_.isTimeOver)
          }

          if (isTimeExpired) {
            val winner = currentPlayer.opposite
            val newState = state.copy(gameResult = model.TimeOut(winner))
            repo.updateGame(gameId, newState)

            // Publish timeout and game ended events to Kafka
            kafkaService.foreach { service =>
              val remainingTime = 0L
              service.publishTimeWarning(gameId, currentPlayer.toString.toLowerCase, remainingTime, isTimeout = true)
                .recover { case ex => println(s"[Kafka] Failed to publish timeout: ${ex.getMessage}") }

              service.publishGameEnded(gameId, newState)
                .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
            }

            Right(newState)
          } else {
            Right(state)
          }
        }
      case None =>
        Left("Game not found")
    }
  }
}
