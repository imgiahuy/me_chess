package gui

import scalafx.scene.layout.{BorderPane, GridPane, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.image.{Image, ImageView}
import model.{Black, Board, Piece, Position, White}
import scalafx.geometry.Insets
import scalafx.scene.control.Button

object GuiRenderer {

  private def pieceImage(piece: Piece): ImageView = {
    val prefix = (piece.color match {
      case White => "w"
      case Black => "b"
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

      grid.add(square, col, 7 - row)
    }

    grid
  }

  def renderGameUI(
                    board: Board,
                    onClick: (Int, Int) => Unit,
                    onExit: () => Unit,
                    onSave: () => Unit,
                    onLoad: () => Unit
                  ): BorderPane = {

    def styledButton(text: String, action: () => Unit): Button = {
      new Button(text) {
        prefWidth = 120
        style =
          """
            | -fx-font-size: 14px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 10 15 10 15;
            | -fx-background-color: linear-gradient(#4CAF50, #2E7D32);
            | -fx-text-fill: white;
            |""".stripMargin

        onAction = _ => action()

        onMouseEntered = _ => style =
          """
            | -fx-font-size: 14px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 10 15 10 15;
            | -fx-background-color: linear-gradient(#66BB6A, #388E3C);
            | -fx-text-fill: white;
            |""".stripMargin

        onMouseExited = _ => style =
          """
            | -fx-font-size: 14px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 10 15 10 15;
            | -fx-background-color: linear-gradient(#4CAF50, #2E7D32);
            | -fx-text-fill: white;
            |""".stripMargin
      }
    }

    val boardView = renderBoard(board, onClick)

    val saveButton = styledButton("Save", onSave)
    val loadButton = styledButton("Load", onLoad)
    val exitButton = styledButton("Exit", onExit)

    val sidePanel = new VBox {
      spacing = 10
      padding = Insets(10)
      children = List(saveButton, loadButton, exitButton)
    }

    new BorderPane {
      center = boardView
      right = sidePanel
    }
  }
}