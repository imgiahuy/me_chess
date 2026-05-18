package tui

import model.Snapshot
import parser.Input
import controller.GameControllerInterface
import parser.Output.uciToModel
/** The interactive game loop. */
case class GameLoop (gameController: GameControllerInterface) {

  private val chessParser = new parser.manualParse.api.ChessParser

  sealed trait Command
  case object ShowBoard             extends Command
  case object ShowHelp              extends Command
  case object Quit                  extends Command
  case class  MakeMove(raw: String) extends Command
  case object SaveGame extends Command
  case object LoadGame extends Command

  /** Maps a trimmed, lower-cased input string to a Command. Pure function. */
  def parseCommand(input: String): Command = {
    val parts = input.trim.toLowerCase.split("\\s+")
    parts.toList match {
      case "board" :: Nil         => ShowBoard
      case "help"  :: Nil         => ShowHelp
      case "quit"  :: Nil         => Quit
      case "save"  :: Nil    => SaveGame
      case "load"  :: Nil    => LoadGame
      case other :: Nil           => MakeMove(other)
      case other                  => MakeMove(other.mkString(" ")) // fallback
    }
  }

  /** Applies a Command to a GameState. */
  def processCommand(state: Snapshot)(cmd: Command): (Snapshot, Option[String]) = {

    def applyMoveCmd(raw: String): (Snapshot, Option[String]) = {

      gameController.makeMove(state, raw) match {

        case Right(newState) =>
          (
            newState,
            Some(s"Moved: $raw")
          )

        case Left(err) =>
          (
            state,
            Some(ConsoleRenderer.renderError(err))
          )
      }
    }

    cmd match {
      case ShowBoard     => (state, Some(ConsoleRenderer.renderBoard(state.board)))
      case ShowHelp      => (state, Some(ConsoleRenderer.renderHelp))
      case Quit          => (state, Some("Goodbye!"))
      case MakeMove(raw) => applyMoveCmd(raw)

      case SaveGame =>
        try {
          val data = gameController.save(state)
          (state, Some(s"Game saved to 'savegame.txt'"))
        } catch {
          case e: Exception => (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }

      case LoadGame =>
        try {
          val loadedState = gameController.load()
          (loadedState, Some(s"Game loaded from 'savegame.txt'"))
        } catch {
          case e: Exception => (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }
    }
  }

  @scala.annotation.tailrec
  final def loop(
            state:     Snapshot,
            readLine:  () => Option[String],
            writeLine: String => Unit,
            isGameOver: Snapshot => Boolean = gameController.isGameOver
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
    loop(gameController.create(), readLine, println)
  }
}