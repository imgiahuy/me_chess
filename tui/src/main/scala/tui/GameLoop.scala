package tui

import controller.GameService
import domain.engine.GameState
import parser.{GameStateFEN, MoveParser}

/** The interactive game loop. */
object GameLoop {

  sealed trait Command
  case object ShowBoard             extends Command
  case object ShowHelp              extends Command
  case object Quit                  extends Command
  case class  MakeMove(raw: String) extends Command
  case class SaveGame(filename: String) extends Command
  case class LoadGame(filename: String) extends Command

  /** Maps a trimmed, lower-cased input string to a Command. Pure function. */
  def parseCommand(input: String): Command = {
    val parts = input.trim.toLowerCase.split("\\s+")
    parts.toList match {
      case "board" :: Nil         => ShowBoard
      case "help"  :: Nil         => ShowHelp
      case "quit"  :: Nil         => Quit
      case "save"  :: filename    => SaveGame(filename.mkString(" "))
      case "load"  :: filename    => LoadGame(filename.mkString(" "))
      case other :: Nil           => MakeMove(other)
      case other                  => MakeMove(other.mkString(" ")) // fallback
    }
  }

  /** Applies a Command to a GameState. */
  def processCommand(state: GameState)(cmd: Command): (GameState, Option[String]) = {

    def applyMoveCmd(raw: String): (GameState, Option[String]) = {
      val result: Either[String, (GameState, String)] = for {
        move <- MoveParser.parse(raw).toRight(s"Invalid move: '$raw'. Use format e.g. e2e4")
        newState <- GameService.applyMove(state, move)
      } yield (newState, s"Moved: ${move.toAlgebraic}")

      result match {
        case Right((s, msg)) => (s, Some(msg))
        case Left(err)       => (state, Some(ConsoleRenderer.renderError(err)))
      }
    }

    cmd match {
      case ShowBoard     => (state, Some(ConsoleRenderer.renderBoard(state.board)))
      case ShowHelp      => (state, Some(ConsoleRenderer.renderHelp))
      case Quit          => (state, Some("Goodbye!"))
      case MakeMove(raw) => applyMoveCmd(raw)

      case SaveGame(filename) =>
        try {
          val data = GameStateFEN.save(state)
          import java.nio.file.{Files, Paths}
          Files.write(Paths.get(filename), data.getBytes)
          (state, Some(s"Game saved to '$filename'"))
        } catch {
          case e: Exception => (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }

      case LoadGame(filename) =>
        try {
          import java.nio.file.{Files, Paths}
          val bytes = Files.readAllBytes(Paths.get(filename))
          val content = new String(bytes)
          val loadedState = GameStateFEN.load(content)
          (loadedState, Some(s"Game loaded from '$filename'"))
        } catch {
          case e: Exception => (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }
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