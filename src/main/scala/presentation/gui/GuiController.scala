package chess.presentation.gui

import chess.domain._
import chess.application.{GameService, MoveParser}
import scalafx.scene.layout.BorderPane

class GuiController {

  private var state: GameState = GameState.initial
  private var selected: Option[(Int, Int)] = None

  val root = new BorderPane()

  def start(): Unit = {
    render()
  }

  private def render(): Unit = {
    root.center = GuiRenderer.renderBoard(state.board, handleClick)
  }

  private def handleClick(col: Int, row: Int): Unit = {
    selected match {

      case None =>
        selected = Some((col, row))

      case Some((fromCol, fromRow)) =>
        val moveStr = s"${toAlg(fromCol, fromRow)}${toAlg(col, row)}"

        val result = for {
          move <- MoveParser.parse(moveStr).toRight("Invalid move")
          newState <- GameService.applyMove(state, move)
        } yield newState

        result match {
          case Right(newState) =>
            state = newState
          case Left(err) =>
            println(err) // later: show in UI
        }

        selected = None
        render()
    }
  }

  private def toAlg(col: Int, row: Int): String = {
    val file = ('a' + col).toChar
    val rank = (row + 1).toString
    s"$file$rank"
  }
}