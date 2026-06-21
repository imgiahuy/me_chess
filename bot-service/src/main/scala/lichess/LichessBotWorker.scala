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
    val initialState = GameService.createGame(
      whitePlayerName = if (myColor == White) "Bot" else full.opponent.flatMap(_.name).getOrElse("Opponent"),
      blackPlayerName = if (myColor == Black) "Bot" else full.opponent.flatMap(_.name).getOrElse("Opponent"),
      timeControl = None
    )

    applyMoves(initialState, full.state.moves) match {
      case Right(state) =>
        activeGames.put(full.id, ActiveGame(full.id, myColor, full.opponent, state, DefaultBot))
        println(s"[LichessBot] Initialized game ${full.id}, playing ${full.color}")
        maybePlayMove(full.id, state)
      case Left(error) =>
        println(s"[LichessBot] Failed to apply initial moves for ${full.id}: $error")
    }
  }

  private def updateGameState(gameId: String, state: GameState): Unit = {
    activeGames.get(gameId) match {
      case Some(active) =>
        if (state.moves == active.lastMoves) {
          // no new moves
          return
        }

        // Validate that state.moves is not shorter than active.lastMoves before dropping
        if (state.moves.length < active.lastMoves.length) {
          println(s"[LichessBot] Invalid state for $gameId: moves shorter than expected")
          return
        }

        val newMoves = state.moves.drop(active.lastMoves.length).trim
        if (newMoves.isEmpty) {
          return
        }

        applyMoves(active.state, newMoves) match {
          case Right(updatedState) =>
            val updated = active.copy(state = updatedState, lastMoves = state.moves)
            activeGames.put(gameId, updated)
            maybePlayMove(gameId, updatedState)
          case Left(error) =>
            println(s"[LichessBot] Failed to apply moves for $gameId: $error")
        }
      case None =>
        println(s"[LichessBot] Received state for unknown game $gameId")
    }
  }

  private def applyMoves(state: model.PositionState, moves: String): Either[String, model.PositionState] = {
    if (moves.trim.isEmpty) {
      Right(state)
    } else {
      UciParser.parseMoveList(moves) match {
        case Right(moveList) =>
          moveList.foldLeft[Either[String, model.PositionState]](Right(state)) { (acc, move) =>
            acc.flatMap(current => GameService.applyMove(current, move))
          }
        case Left(error) =>
          Left(error)
      }
    }
  }

  private def maybePlayMove(gameId: String, state: model.PositionState): Unit = {
    activeGames.get(gameId) match {
      case Some(active) if state.turn == active.myColor && state.gameResult == model.Ongoing =>
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
      case _ =>
      // not our turn or game is over
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
