package chess.presentation.tui

import chess.domain._
import chess.application.{GameService, MoveParser}

/** The interactive game loop. */
object GameLoop {

  sealed trait Command
  case object ShowBoard             extends Command
  case object ShowHelp              extends Command
  case object Quit                  extends Command
  case class  MakeMove(raw: String) extends Command

  /** Maps a trimmed, lower-cased input string to a Command. Pure function. */
  def parseCommand(input: String): Command =
    input.trim.toLowerCase match {
      case "board" => ShowBoard
      case "help"  => ShowHelp
      case "quit"  => Quit
      case other   => MakeMove(other)
    }

  /** Applies a Command to a GameState. */
  def processCommand(state: GameState)(cmd: Command): (GameState, Option[String]) = {

    // ── Helper for applying moves ──────────────────────────────────────────────
    val applyMoveCmd: String => (GameState, Option[String]) = raw => {
      // Chain Option -> Either -> Either using for-comprehension
      val result: Either[String, (GameState, String)] = for {
        move <- MoveParser.parse(raw).toRight(s"Invalid move: '$raw'. Use format e.g. e2e4")
        newState <- GameService.applyMove(state, move)
      } yield (newState, s"Moved: ${move.toAlgebraic}")

      result match {
        case Right((s, msg)) => (s, Some(msg))
        case Left(err)       => (state, Some(ConsoleRenderer.renderError(err)))
      }
    }

    // ── Main match on command ────────────────────────────────────────────────
    cmd match {
      case ShowBoard     => (state, Some(ConsoleRenderer.renderBoard(state.board)))
      case ShowHelp      => (state, Some(ConsoleRenderer.renderHelp))
      case Quit          => (state, Some("Goodbye!"))
      case MakeMove(raw) => applyMoveCmd(raw)
    }
  }

  @scala.annotation.tailrec
  def loop(
            state:     GameState,
            readLine:  () => Option[String],
            writeLine: String => Unit,
            isGameOver: GameState => Boolean = GameService.isGameOver
          ): Unit = {
    if (isGameOver(state)) {
      writeLine(ConsoleRenderer.renderBoard(state.board))
      writeLine(ConsoleRenderer.renderOutcome(state))
    } else {
      writeLine(ConsoleRenderer.renderGameState(state))
      writeLine("> ")
      readLine() match {
        case None =>
          writeLine("Goodbye!")
        case Some(raw) =>
          parseCommand(raw) match {
            case Quit =>
              writeLine("Goodbye!")
            case cmd =>
              val (nextState, msg) = processCommand(state)(cmd)
              msg.foreach(writeLine)
              loop(nextState, readLine, writeLine)
          }
      }
    }
  }

  /** Wires real stdin/stdout and starts the game. Only impure function. */
  def start(): Unit = {
    val readLine: () => Option[String] = () => Option(scala.io.StdIn.readLine())
    println(ConsoleRenderer.renderHelp)
    loop(GameState.initial, readLine, println)
  }
}