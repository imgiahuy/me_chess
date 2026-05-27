package gui

import controller.GameControllerInterface
import scalafx.scene.layout.BorderPane
import scalafx.scene.control.{Alert, Button, ButtonType, Dialog, TextField}
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.Scene
import scalafx.stage.Stage
import scalafx.Includes.*
import model.PositionState

class GuiController(gameControllerInterface: GameControllerInterface) {

  private var state: PositionState = null
  private var selected: Option[(Int, Int)] = None
  private var isProcessingMove: Boolean = false
  private var validMoves: Set[(Int, Int)] = Set()
  private var showingSetup: Boolean = true
  private var moveHistory: List[String] = List()
  private var notification: String = "Welcome to Chess!"

  val root = new BorderPane()

  def start(): Unit = {
    showPlayerSetup()
  }

  private def showPlayerSetup(): Unit = {
    showingSetup = true
    root.center = GuiRenderer.renderPlayerSetup(
      handleStartGame,
      handleExit
    )
  }

  private def render(): Unit = {
    showingSetup = false
    root.center = GuiRenderer.renderGameUI(
      state.board,
      handleClick,
      handleExit,
      handleSave,
      handleLoad,
      handleExportPgn,
      selected,
      validMoves,
      moveHistory,
      notification
    )
  }

  private def handleClick(col: Int, row: Int): Unit = {
    // Prevent rapid clicks from being processed
    if (isProcessingMove) {
      return
    }

    selected match {

      case None =>
        // First click: select a piece
        if (state.board.pieceAt(model.Position(col, row)).isDefined) {
          selected = Some((col, row))
          // TODO: Calculate valid moves for the selected piece
          validMoves = Set()
          render()
        }

      case Some((fromCol, fromRow)) =>
        // Second click: attempt to move
        if ((col, row) == (fromCol, fromRow)) {
          // Deselect if clicking the same square
          selected = None
          validMoves = Set()
          render()
        } else {
          isProcessingMove = true
          val moveStr = s"${toAlg(fromCol, fromRow)}${toAlg(col, row)}"

          val result = gameControllerInterface.makeMove(state, moveStr)

          result match {
            case Right(newState) =>
              state = newState
              selected = None
              validMoves = Set()
              moveHistory = moveHistory :+ moveStr
              notification = s"Move: $moveStr"
            case Left(err) =>
              println(err)
              notification = s"Invalid move: $err"
              // Invalid move: keep selection and show error
          }

          isProcessingMove = false
          render()
        }
    }
  }

  private def handleExit(): Unit = {
    println("Goodbye!")
    sys.exit(0)
  }

  private def handleSave(): Unit = {
    try {
      gameControllerInterface.save(state)
      notification = "Game saved successfully!"
      render()
    } catch {
      case e: Exception =>
        notification = s"Error saving: ${e.getMessage}"
        render()
    }
  }

  private def handleLoad(): Unit = {
    try {
      state = gameControllerInterface.load()
      selected = None
      validMoves = Set()
      moveHistory = state.moveHistory.map(move => s"${toAlg(move.from.col, move.from.row)}${toAlg(move.to.col, move.to.row)}")
      notification = "Game loaded successfully!"
      render()
    } catch {
      case e: Exception =>
        notification = s"Error loading: ${e.getMessage}"
        render()
    }
  }

  private def toAlg(col: Int, row: Int): String = {
    val file = ('a' + col).toChar
    val rank = (row + 1).toString
    s"$file$rank"
  }

  private def handleStartGame(whiteName: String, blackName: String): Unit = {
    val whitePlayer = if (whiteName.trim.nonEmpty) whiteName.trim else "White"
    val blackPlayer = if (blackName.trim.nonEmpty) blackName.trim else "Black"
    
    state = gameControllerInterface.create(whitePlayer, blackPlayer)
    moveHistory = List()
    notification = s"Game started: $whitePlayer vs $blackPlayer"
    render()
  }

  private def handleExportPgn(): Unit = {
    try {
      gameControllerInterface.exportToPgn(state, "GUI Game", "Local", "game.pgn")
      notification = "Game exported successfully to 'game.pgn'"
      render()
    } catch {
      case e: Exception =>
        notification = s"Failed to export game: ${e.getMessage}"
        render()
    }
  }
}
