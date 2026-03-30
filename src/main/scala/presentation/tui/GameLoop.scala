package chess.presentation.tui

import chess.domain._
import chess.application.{GameService, MoveParser}

/** The interactive game loop.
 *
 * Design principles:
 *   - The loop itself is tail-recursive (`@tailrec`), no mutable state.
 *   - I/O is injected via `readLine` and `writeLine` parameters, making
 *     every function here fully unit-testable without touching stdin/stdout.
 *   - Command parsing and state transitions are pure functions that can be
 *     tested in isolation.
 *
 * The only impure function is `start()`, which wires real stdin/stdout and
 * is the single entry point from `Main`.
 */
object GameLoop {

  // ── Command ADT ──────────────────────────────────────────────────────────────

  sealed trait Command
  case object ShowBoard             extends Command
  case object ShowHelp              extends Command
  case object Quit                  extends Command
  case class  MakeMove(raw: String) extends Command

  // ── Command parsing ──────────────────────────────────────────────────────────

  /** Maps a trimmed, lower-cased input string to a Command. Pure function. */
  def parseCommand(input: String): Command =
    input.trim.toLowerCase match {
      case "board" => ShowBoard
      case "help"  => ShowHelp
      case "quit"  => Quit
      case other   => MakeMove(other)
    }

  // ── Command processing ───────────────────────────────────────────────────────

  /** Applies a Command to a GameState.
   *
   * Returns (newState, optionalMessage).  State is unchanged for display-only
   * commands.  A partially-applied helper `applyMoveCmd` is extracted to keep
   * the match arms short.
   *
   * Curried form used so callers can partially apply the state:
   *   val handle = processCommand(currentState) _
   */
  def processCommand(state: GameState)(cmd: Command): (GameState, Option[String]) = {
    val applyMoveCmd: String => (GameState, Option[String]) = raw =>
      MoveParser.parse(raw) match {
        case None =>
          (state, Some(ConsoleRenderer.renderError(s"Invalid move: '$raw'. Use format e.g. e2e4")))
        case Some(move) =>
          GameService.applyMove(state, move) match {
            case Left(err)       => (state, Some(ConsoleRenderer.renderError(err)))
            case Right(newState) => (newState, Some(s"Moved: ${move.toAlgebraic}"))
          }
      }

    cmd match {
      case ShowBoard       => (state, Some(ConsoleRenderer.renderBoard(state.board)))
      case ShowHelp        => (state, Some(ConsoleRenderer.renderHelp))
      case Quit            => (state, Some("Goodbye!"))
      case MakeMove(raw)   => applyMoveCmd(raw)
    }
  }

  // ── Main loop ────────────────────────────────────────────────────────────────

  /** Tail-recursive game loop.
   *
   * Termination conditions:
   *   - `readLine()` returns None  (EOF / stream exhausted)
   *   - User types "quit"
   *   - `GameEngine.isGameOver` becomes true
   *
   * @param state     current game state (immutable; threaded through recursion)
   * @param readLine  supplier of the next line; None signals end-of-input
   * @param writeLine consumer of output lines (e.g. println)
   */
  @scala.annotation.tailrec
  def loop(
            state:     GameState,
            readLine:  () => Option[String],
            writeLine: String => Unit
          ): Unit = {
    if (GameService.isGameOver(state)) {
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

  // ── Entry point ──────────────────────────────────────────────────────────────

  /** Wires real stdin/stdout and starts the game.  Only impure function. */
  def start(): Unit = {
    val readLine: () => Option[String] = () => Option(scala.io.StdIn.readLine())
    println(ConsoleRenderer.renderHelp)
    loop(GameState.initial, readLine, println)
  }
}