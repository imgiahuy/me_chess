package chess.presentation.gui

import scalafx.scene.layout.{GridPane, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.image.{Image, ImageView}
import chess.domain._

object GuiRenderer {

  private def pieceImage(piece: Piece): ImageView = {
    val prefix = (piece.color match {
      case chess.domain.White => "w"
      case chess.domain.Black => "b"
    }) + piece.symbol.toLower

    new ImageView(new Image(s"/pieces/$prefix.png")) {
      fitWidth = 60
      fitHeight = 60
    }
  }

  def renderBoard(
                   board: Board,
                   onClick: (Int, Int) => Unit
                 ): GridPane = {

    val grid = new GridPane()

    for (row <- 0 until 8; col <- 0 until 8) {

      val isLight = (row + col) % 2 == 0

      val square = new StackPane()

      val rect = new Rectangle {
        width = 80
        height = 80
        fill = if (isLight) Color.Beige else Color.SandyBrown
      }

      square.children.add(rect)

      board.pieceAt(Position(col, row)).foreach { piece =>
        square.children.add(pieceImage(piece))
      }

      square.onMouseClicked = _ => onClick(col, row)

      grid.add(square, col, 7 - row) // flip like your TUI
    }

    grid
  }
}