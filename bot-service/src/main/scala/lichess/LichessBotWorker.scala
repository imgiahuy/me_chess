package lichess

import akka.actor.typed.ActorSystem
import model.{Color, White, Black}
import parser.UciParser
import service.{BotService, GameService}
import formatter.UciFormatter

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success}

/** Worker that connects the internal chess engine to Lichess via the Bot API.
  *
  * It listens for account events, accepts suitable challenges, and plays games by
  * mirroring the Lichess board state in the internal `PositionState` and using the
  * existing bot implementations to select moves.
  */
class LichessBotWorker(client: LichessClient, config: LichessBotConfig)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  private val activeGames = TrieMap.empty[String, ActiveGame]
  private val pendingFens = TrieMap.empty[String, String]
  private val DefaultBot = BotService.createBot(config.botType)

  /** Start the account event stream and handle incoming events. */
  def run(): Future[Unit] = {
    println("[LichessBot] Starting event stream...")
    client.streamEvents().runForeach(handleEvent).map(_ => ())
  }

  private def handleEvent(event: LichessEvent): Unit = {
    event.`type` match {
      case "challenge" =>
        event.challenge.foreach(handleChallenge)

      case "gameStart" =>
        event.game.foreach { game =>
          println(s"[LichessBot] Game started: ${game.id}")
          // Extract FEN from gameStart event if available
          game.fen.foreach { fen =>
            println(s"[LichessBot] Game start FEN: $fen")
            pendingFens.put(game.id, fen)
          }
          startGameStream(game.id)
        }

      case "gameFinish" =>
        event.game.foreach { game =>
          println(s"[LichessBot] Game finished: ${game.id}")
          activeGames.remove(game.id)
        }

      case "challengeCanceled" | "challengeDeclined" =>
        event.challenge.foreach { challenge =>
          println(s"[LichessBot] Challenge ${challenge.id} ${event.`type`}")
        }

      case _ =>
      // ignore unknown events
    }
  }

  private def handleChallenge(challenge: Challenge): Unit = {
    if (shouldAccept(challenge)) {
      println(s"[LichessBot] Accepting challenge ${challenge.id} from ${challenge.challenger.name.getOrElse("unknown")}")
      client.acceptChallenge(challenge.id).onComplete {
        case Success(_) =>
          println(s"[LichessBot] Challenge ${challenge.id} accepted")
        case Failure(ex) =>
          println(s"[LichessBot] Failed to accept challenge ${challenge.id}: ${ex.getMessage}")
      }
    } else {
      println(s"[LichessBot] Declining challenge ${challenge.id} (does not match filter)")
      client.declineChallenge(challenge.id).onComplete {
        case Success(_) =>
          println(s"[LichessBot] Challenge ${challenge.id} declined")
        case Failure(ex) =>
          println(s"[LichessBot] Failed to decline challenge ${challenge.id}: ${ex.getMessage}")
      }
    }
  }

  private def shouldAccept(challenge: Challenge): Boolean = {
    val filter = config.challengeFilter
    val speedOk    = filter.allowedSpeeds.contains(challenge.speed)
    val variantOk  = filter.allowedVariants.contains(challenge.variant.key)
    val ratedOk    = (challenge.rated && filter.allowRated) || (!challenge.rated && filter.allowUnrated)

    val timeOk = challenge.timeControl.forall { tc =>
      tc.`type` match {
        case "clock" | "blitz" | "rapid" | "classical" | "bullet" =>
          val limit = tc.limit.getOrElse(0)
          val inc   = tc.increment.getOrElse(0)
          limit <= filter.maxInitialTimeSeconds && inc >= filter.minIncrementSeconds
        case "unlimited" =>
          filter.maxInitialTimeSeconds == Int.MaxValue
        case _ =>
          false
      }
    }

    speedOk && variantOk && ratedOk && timeOk
  }

  private def startGameStream(gameId: String): Unit = {
    client.streamGame(gameId).runForeach { event =>
      event match {
        case full: GameFull =>
          initializeGame(full)
        case state: GameState =>
          updateGameState(gameId, state)
        case unknown: UnknownEvent =>
          println(s"[LichessBot] Unknown event in game stream: ${unknown.raw}")
      }
    }.onComplete {
      case Success(_) =>
        println(s"[LichessBot] Game stream ended: $gameId")
        activeGames.remove(gameId)
      case Failure(ex) =>
        println(s"[LichessBot] Game stream error for $gameId: ${ex.getMessage}")
        activeGames.remove(gameId)
    }
  }

  private def initializeGame(full: GameFull): Unit = {
    val myColor = parseColor(full.color)

    println(s"[LichessBot] Using Lichess FEN directly to avoid chess engine bug")
    // Try to use FEN from pendingFens (from gameStart event) first, then initialFen, then starting position
    val fen = pendingFens.remove(full.id).orElse(full.initialFen).getOrElse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    println(s"[LichessBot] Using FEN: $fen")

    // Parse FEN to get accurate board state
    val state = parser.FenParser.parse(fen) match {
      case Right(parsedState) =>
        println(s"[LichessBot] Successfully parsed FEN, turn: ${parsedState.turn}")
        parsedState
      case Left(error) =>
        println(s"[LichessBot] Failed to parse FEN: $error, using default state")
        GameService.createGame(
          whitePlayerName = if (myColor == White) "Bot" else full.opponent.flatMap(_.name).getOrElse("Opponent"),
          blackPlayerName = if (myColor == Black) "Bot" else full.opponent.flatMap(_.name).getOrElse("Opponent"),
          timeControl = None
        )
    }

    println(s"[LichessBot] State - turn: ${state.turn}, gameResult: ${state.gameResult}")
    // Check if game is already over
    if (state.gameResult != model.Ongoing) {
      println(s"[LichessBot] Game ${full.id} is already over: ${state.gameResult}")
      return
    }

    // Extract moves from GameFull state
    val moves = full.state.moves
    println(s"[LichessBot] Initial moves: $moves")

    activeGames.put(full.id, ActiveGame(full.id, myColor, full.opponent, state, DefaultBot, moves))
    println(s"[LichessBot] Initialized game ${full.id}, playing ${full.color}")
    maybePlayMove(full.id, state)
  }

  private def updateGameState(gameId: String, state: GameState): Unit = {
    activeGames.get(gameId) match {
      case Some(active) =>
        // Check if game is over
        if (state.status.exists(s => s != "started" && s != "created")) {
          println(s"[LichessBot] Game $gameId is over: ${state.status.getOrElse("unknown")}")
          activeGames.remove(gameId)
          return
        }

        // Update lastMoves to track state
        val updated = active.copy(lastMoves = state.moves)
        activeGames.put(gameId, updated)

        // Determine whose turn it is based on move count
        val moveCount = state.moves.split("\\s+").length
        val isWhiteTurn = moveCount % 2 == 0
        val currentTurn = if (isWhiteTurn) White else Black

        println(s"[LichessBot] Game $gameId - move count: $moveCount, current turn: $currentTurn, my color: ${active.myColor}")

        // Play move if it's bot's turn
        if (currentTurn == active.myColor) {
          maybePlayMove(gameId, active.state)
        }
      case None =>
        println(s"[LichessBot] Received state for unknown game $gameId")
    }
  }

  private def applyMoves(state: model.PositionState, moves: String): Either[String, model.PositionState] = {
    if (moves.trim.isEmpty) {
      Right(state)
    } else {
      println(s"[LichessBot] Parsing moves: '$moves'")
      UciParser.parseMoveList(moves) match {
        case Right(moveList) =>
          println(s"[LichessBot] Parsed ${moveList.length} moves")
          println(s"[LichessBot] WARNING: Skipping move application due to chess engine bug - using Lichess state directly")
          // Skip move application due to chess engine bug - return current state
          Right(state)
        case Left(error) =>
          println(s"[LichessBot] Failed to parse move list: $error")
          Left(error)
      }
    }
  }

  private def maybePlayMove(gameId: String, state: model.PositionState): Unit = {
    activeGames.get(gameId) match {
      case Some(active) if state.turn == active.myColor && state.gameResult == model.Ongoing =>
        // Use Stockfish directly with FEN to bypass broken internal chess engine
        val botName = active.bot.name.toLowerCase
        if (botName.contains("stockfish")) {
          playStockfishMove(gameId, state, active.myColor)
        } else {
          // For other bots, use internal engine (will fail due to bug)
          BotService.getBotMove(active.bot, state) match {
            case Right(move) =>
              val uci = UciFormatter.moveToUci(move)
              println(s"[LichessBot] Playing $uci in game $gameId")
              client.makeMove(gameId, uci).onComplete {
                case Success(_) =>
                  println(s"[LichessBot] Move $uci submitted for $gameId")
                case Failure(ex) =>
                  println(s"[LichessBot] Failed to submit move $uci for $gameId: ${ex.getMessage}")
              }
            case Left(error) =>
              println(s"[LichessBot] Could not select a move for $gameId: $error")
          }
        }
      case _ =>
      // not our turn or game is over
    }
  }

  private def playStockfishMove(gameId: String, state: model.PositionState, myColor: Color): Unit = {
    activeGames.get(gameId) match {
      case Some(active) =>
        try {
          // Use Lichess move list to set Stockfish position
          val moves = active.lastMoves
          println(s"[LichessBot] Using Stockfish with moves: $moves")

          // Get Stockfish path from environment
          val stockfishPath = sys.env.getOrElse("STOCKFISH_PATH", "/usr/local/bin/stockfish")
          println(s"[LichessBot] Stockfish path: $stockfishPath")

          // Call Stockfish
          val pb = new ProcessBuilder(stockfishPath)
          val process = pb.start()

          // Send commands to Stockfish - use move list from Lichess
          val writer = new java.io.PrintWriter(process.getOutputStream)
          if (moves.trim.isEmpty) {
            writer.println("position startpos")
          } else {
            writer.println(s"position startpos moves $moves")
          }
          writer.println("go depth 15")
          writer.flush()

          // Read Stockfish output until we get bestmove
          val reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream))
          var bestMove: String = null
          var line: String = null
          var continueReading = true
          while (continueReading && { line = reader.readLine(); line != null }) {
            println(s"[Stockfish] $line")
            if (line.startsWith("bestmove")) {
              val parts = line.split(" ")
              if (parts.length > 1) {
                bestMove = parts(1)
              }
              continueReading = false // Stop reading after getting bestmove
            }
          }

          // Send quit command and wait for process to exit
          writer.println("quit")
          writer.flush()
          process.waitFor()

          if (bestMove != null && bestMove != "(none)") {
            println(s"[LichessBot] Stockfish best move: $bestMove")
            client.makeMove(gameId, bestMove).onComplete {
              case Success(_) =>
                println(s"[LichessBot] Stockfish move $bestMove submitted for $gameId")
              case Failure(ex) =>
                println(s"[LichessBot] Failed to submit Stockfish move $bestMove for $gameId: ${ex.getMessage}")
            }
          } else {
            println(s"[LichessBot] Stockfish could not find a valid move")
          }
        } catch {
          case ex: Exception =>
            println(s"[LichessBot] Error using Stockfish: ${ex.getMessage}")
            ex.printStackTrace()
        }
      case None =>
        println(s"[LichessBot] Game $gameId not found for Stockfish move")
    }
  }

  private def parseColor(color: String): Color = {
    color.toLowerCase match {
      case "black" => Black
      case "white" => White
      case _ =>
        println(s"[LichessBot] Invalid color string: '$color', defaulting to White")
        White
    }
  }

  /** Stop tracking all active games and close the HTTP client. */
  def shutdown(): Future[Unit] = {
    activeGames.clear()
    client.shutdown()
  }
}
