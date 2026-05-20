package gui

import controller.GameControllerInterface
import scalafx.scene.layout.BorderPane
import model.PositionState

class GuiController(gameControllerInterface: GameControllerInterface) {

  private var state: PositionState = gameControllerInterface.create()
  private var selected: Option[(Int, Int)] = None
  private var isProcessingMove: Boolean = false
  private var validMoves: Set[(Int, Int)] = Set()

  val root = new BorderPane()

  def start(): Unit = {
    render()
  }

  private def render(): Unit = {
    root.center = GuiRenderer.renderGameUI(
      state.board,
      handleClick,
      handleExit,
      handleSave,
      handleLoad,
      selected,
      validMoves
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
            case Left(err) =>
              println(err)
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
      println("Game saved successfully!")
    } catch {
      case e: Exception =>
        println(s"Error saving: ${e.getMessage}")
    }
  }

  private def handleLoad(): Unit = {
    try {
      state = gameControllerInterface.load()
      selected = None
      validMoves = Set()
      render()
      println("Game loaded successfully!")
    } catch {
      case e: Exception =>
        println(s"Error loading: ${e.getMessage}")
    }
  }

  private def toAlg(col: Int, row: Int): String = {
    val file = ('a' + col).toChar
    val rank = (row + 1).toString
    s"$file$rank"
  }
}
