package tui

import model.{PositionState, Color, White, Black}
import controller.GameControllerInterface
import service.{GameService, BotService}

/** The interactive game loop with enhanced features.
 *
 * Supports:
 * - UCI move notation (e2e4, g1f3, etc.)
 * - Game status display (check, checkmate, stalemate)
 * - Move history tracking
 * - Save/load functionality
 * - Resignation
 * - Bot moves
 * - Real-time game state validation
 */
case class GameLoop(gameController: GameControllerInterface) {
  
  sealed trait Command
  case object ShowBoard             extends Command
  case object ShowHelp              extends Command
  case object ShowStatus            extends Command
  case object ShowMoves             extends Command
  case object Quit                  extends Command
  case class  MakeMove(raw: String) extends Command
  case object SaveGame              extends Command
  case object LoadGame              extends Command
  case object ExportPgn             extends Command
  case object ResignWhite           extends Command
  case object ResignBlack           extends Command
  case class  BotMove(botType: String) extends Command

  /** Maps a trimmed, lower-cased input string to a Command. Pure function. */
  def parseCommand(input: String): Command = {
    val parts = input.trim.toLowerCase.split("\\s+")
    parts.toList match {
      case "board" :: Nil         => ShowBoard
      case "status" :: Nil        => ShowStatus
      case "moves" :: Nil         => ShowMoves
      case "help"  :: Nil         => ShowHelp
      case "quit"  :: Nil         => Quit
      case "resign" :: Nil        => ResignWhite  // Default to current player
      case "resign" :: "white" :: Nil => ResignWhite
      case "resign" :: "black" :: Nil => ResignBlack
      case "save"  :: Nil         => SaveGame
      case "load"  :: Nil         => LoadGame
      case "export" :: "pgn" :: Nil => ExportPgn
      case "bot" :: botType :: Nil => BotMove(botType)
      case other :: Nil           => MakeMove(other)
      case other                  => MakeMove(other.mkString(" "))
    }
  }

  /** Applies a Command to a GameState. */
  def processCommand(state: PositionState)(cmd: Command): (PositionState, Option[String]) = {

    def applyMoveCmd(raw: String): (PositionState, Option[String]) = {
      gameController.makeMove(state, raw) match {
        case Right(newState) =>
          val moveNotation = s"${raw.take(2)}-${raw.drop(2)}"
          val message = if GameService.isCheckmate(newState) then
            s"Moved: $moveNotation - CHECKMATE! ${newState.turn.opposite} wins!"
          else if GameService.isStalemate(newState) then
            s"Moved: $moveNotation - STALEMATE! Draw."
          else if GameService.isKingInCheck(newState.board, newState.turn) then
            s"Moved: $moveNotation - CHECK!"
          else
            s"Moved: $moveNotation"
          (newState, Some(message))

        case Left(err) =>
          (state, Some(ConsoleRenderer.renderError(err)))
      }
    }

    cmd match {
      case ShowBoard =>
        // Return empty message since board is already shown in renderGameState()
        (state, None)

      case ShowStatus =>
        (state, Some(ConsoleRenderer.renderGameStatus(state)))

      case ShowMoves =>
        (state, Some(ConsoleRenderer.renderMoveHistory(state)))

      case ShowHelp =>
        (state, Some(ConsoleRenderer.renderHelp))

      case Quit =>
        (state, Some("Goodbye!"))

      case MakeMove(raw) =>
        applyMoveCmd(raw)

      case SaveGame =>
        try {
          gameController.save(state)
          (state, Some(ConsoleRenderer.renderSuccess("Game saved to 'savegame.txt'")))
        } catch {
          case e: Exception =>
            (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }

      case LoadGame =>
        try {
          val loadedState = gameController.load()
          (loadedState, Some(ConsoleRenderer.renderSuccess("Game loaded from 'savegame.txt'")))
        } catch {
          case e: Exception =>
            (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }

      case ExportPgn =>
        try {
          gameController.exportToPgn(state, "TUI Game", "Local", "game.pgn")
          (state, Some(ConsoleRenderer.renderSuccess("Game exported to 'game.pgn'")))
        } catch {
          case e: Exception =>
            (state, Some(ConsoleRenderer.renderError(e.getMessage)))
        }

      case ResignWhite =>
        if (state.turn == White) {
          val newState = GameService.resign(state, White)
          (newState, Some(ConsoleRenderer.renderSuccess("White resigned - Black wins!")))
        } else {
          (state, Some(ConsoleRenderer.renderError("Cannot resign on opponent's turn")))
        }

      case ResignBlack =>
        if (state.turn == Black) {
          val newState = GameService.resign(state, Black)
          (newState, Some(ConsoleRenderer.renderSuccess("Black resigned - White wins!")))
        } else {
          (state, Some(ConsoleRenderer.renderError("Cannot resign on opponent's turn")))
        }

      case BotMove(botType) =>
        try {
          val bot = BotService.createBot(botType)
          BotService.playBotMove(bot, state) match {
            case Right(newState) =>
              val message = if GameService.isCheckmate(newState) then
                s"Bot ($botType) moved - CHECKMATE! ${newState.turn.opposite} wins!"
              else if GameService.isStalemate(newState) then
                s"Bot ($botType) moved - STALEMATE! Draw."
              else if GameService.isKingInCheck(newState.board, newState.turn) then
                s"Bot ($botType) moved - CHECK!"
              else
                s"Bot ($botType) moved"
              (newState, Some(message))
            case Left(err) =>
              (state, Some(ConsoleRenderer.renderError(s"Bot error: $err")))
          }
        } catch {
          case e: Exception =>
            (state, Some(ConsoleRenderer.renderError(s"Failed to create bot: ${e.getMessage}")))
        }
    }
  }

  @scala.annotation.tailrec
  final def loop(
    state: PositionState,
    readLine: () => Option[String],
    writeLine: String => Unit,
    isGameOver: PositionState => Boolean = gameController.isGameOver
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
    
    // Prompt for player names
    println("=== Chess Game Setup ===")
    println("Enter player names (press Enter for defaults):")
    println("White player name:")

    val whiteName = readLine() match {
      case Some(name) if name.trim.nonEmpty => name.trim
      case _ => "White"
    }
    
    println("Black player name:")
    val blackName = readLine() match {
      case Some(name) if name.trim.nonEmpty => name.trim
      case _ => "Black"
    }
    
    println(s"\nStarting game: $whiteName vs $blackName")
    println(ConsoleRenderer.renderHelp)
    
    loop(gameController.create(whiteName, blackName), readLine, println)
  }
}
