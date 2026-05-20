package gui

import scalafx.scene.layout.{BorderPane, GridPane, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.image.{Image, ImageView}
import model.{Black, Board, Piece, Position, White}
import scalafx.geometry.Insets
import scalafx.scene.control.Button
import scalafx.animation.{Timeline, KeyFrame}
import scalafx.animation.{Timeline, KeyFrame, KeyValue}
import scalafx.util.Duration
import scalafx.event.ActionEvent

object GuiRenderer {

  private var boardGridPane: Option[GridPane] = None
  private val squareCache = scala.collection.mutable.Map[(Int, Int), StackPane]()

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
                   onClick: (Int, Int) => Unit,
                   selected: Option[(Int, Int)],
                   validMoves: Set[(Int, Int)]
                 ): GridPane = {

    val grid = new GridPane()
    squareCache.clear()

    for (row <- 0 until 8; col <- 0 until 8) {

      val isLight = (row + col) % 2 == 0
      val isSelected = selected.contains((col, row))
      val isValidMove = validMoves.contains((col, row))

      val square = new StackPane()

      val rect = new Rectangle {
        width = 80
        height = 80
        fill = if (isSelected) {
          Color.web("#FFEB3B")  // Yellow for selected
        } else if (isValidMove) {
          Color.web("#81C784")  // Green for valid moves
        } else if (isLight) {
          Color.Beige
        } else {
          Color.SandyBrown
        }
      }

      square.children.add(rect)

      board.pieceAt(Position(col, row)).foreach { piece =>
        square.children.add(pieceImage(piece))
      }

      // Add hover effect
      square.onMouseEntered = _ => {
        rect.fill = if (isSelected) {
          Color.web("#FDD835")
        } else if (isValidMove) {
          Color.web("#66BB6A")
        } else if (isLight) {
          Color.web("#F5F5DC")
        } else {
          Color.web("#D2B48C")
        }
      }

      square.onMouseExited = _ => {
        rect.fill = if (isSelected) {
          Color.web("#FFEB3B")
        } else if (isValidMove) {
          Color.web("#81C784")
        } else if (isLight) {
          Color.Beige
        } else {
          Color.SandyBrown
        }
      }

      square.onMouseClicked = _ => onClick(col, row)

      grid.add(square, col, 7 - row)
      squareCache((col, row)) = square
    }

    boardGridPane = Some(grid)
    grid
  }

  def animatePieceMove(fromCol: Int, fromRow: Int, toCol: Int, toRow: Int, durationMs: Int): Unit = {
    boardGridPane.foreach { grid =>
      squareCache.get((fromCol, fromRow)).foreach { fromSquare =>
        squareCache.get((toCol, toRow)).foreach { toSquare =>
          // Get the piece image from source square
          val pieceImages = fromSquare.children.filter(_.isInstanceOf[ImageView])
          
          pieceImages.foreach { pieceNode =>
            val imageView = pieceNode.asInstanceOf[ImageView]
            
            // Animate to destination
            val duration = Duration.apply(durationMs)
            val keyFrame = KeyFrame(
              time = Duration(durationMs),
              onFinished = _ => {
                imageView.translateX = 0
                imageView.translateY = 0
                fromSquare.children.remove(imageView)
                toSquare.children.add(imageView)
              },
              values = Set(
                KeyValue(imageView.translateXProperty(), (toCol - fromCol) * 80.0),
                KeyValue(imageView.translateYProperty(), (toRow - fromRow) * 80.0)
              )
            )
            val animTimeline = new Timeline {
              cycleCount = 1
              autoReverse = false
              keyFrames = Seq(keyFrame)
            }
            
            animTimeline.play()
          }
        }
      }
    }
  }

  def renderGameUI(
                    board: Board,
                    onClick: (Int, Int) => Unit,
                    onExit: () => Unit,
                    onSave: () => Unit,
                    onLoad: () => Unit,
                    selected: Option[(Int, Int)],
                    validMoves: Set[(Int, Int)]
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

    val boardView = renderBoard(board, onClick, selected, validMoves)

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
