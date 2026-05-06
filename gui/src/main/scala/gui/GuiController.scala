package gui

import controller.GameControllerInterface
import scalafx.scene.layout.BorderPane
import model.Snapshot

class GuiController(gameControllerInterface: GameControllerInterface) {

  private var state: Snapshot = gameControllerInterface.create()
  private var selected: Option[(Int, Int)] = None

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
      handleLoad
    )
  }

  private def handleClick(col: Int, row: Int): Unit = {
    selected match {

      case None =>
        selected = Some((col, row))

      case Some((fromCol, fromRow)) =>
        val moveStr = s"${toAlg(fromCol, fromRow)}${toAlg(col, row)}"

        val result = gameControllerInterface.makeMove(state, moveStr)


        result match {
          case Right(newState) =>
            state = newState
          case Left(err) =>
            println(err)
        }

        selected = None
        render()
    }
  }

  private def handleExit(): Unit = {
    println("Goodbye!")
    sys.exit(0)
  }

  private def handleSave(): Unit = {
    try {
      gameControllerInterface.save(state)
    } catch {
      case e: Exception =>
        println(s"Error saving: ${e.getMessage}")
    }
  }

  private def handleLoad(): Unit = {
    try {
      state = gameControllerInterface.load()
      selected = None
      render()
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