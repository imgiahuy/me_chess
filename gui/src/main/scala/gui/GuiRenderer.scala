package gui

import scalafx.scene.layout.{BorderPane, GridPane, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.image.{Image, ImageView}
import model.{Black, Board, Piece, Position, White}
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, TextField}
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.animation.{KeyFrame, KeyValue, Timeline}
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
                    onExportPgn: () => Unit,
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
    val exportButton = styledButton("Export PGN", onExportPgn)
    val exitButton = styledButton("Exit", onExit)

    val sidePanel = new VBox {
      spacing = 10
      padding = Insets(10)
      children = List(saveButton, loadButton, exportButton, exitButton)
    }

    new BorderPane {
      center = boardView
      right = sidePanel
    }
  }

  def renderPlayerSetup(
    onStartGame: (String, String) => Unit,
    onExit: () => Unit
  ): BorderPane = {
    
    def styledButton(text: String, action: () => Unit): Button = {
      new Button(text) {
        prefWidth = 150
        style =
          """
            | -fx-font-size: 16px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 15 20 15 20;
            | -fx-background-color: linear-gradient(#4CAF50, #2E7D32);
            | -fx-text-fill: white;
            |""".stripMargin

        onAction = _ => action()

        onMouseEntered = _ => style =
          """
            | -fx-font-size: 16px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 15 20 15 20;
            | -fx-background-color: linear-gradient(#66BB6A, #388E3C);
            | -fx-text-fill: white;
            |""".stripMargin

        onMouseExited = _ => style =
          """
            | -fx-font-size: 16px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 15 20 15 20;
            | -fx-background-color: linear-gradient(#4CAF50, #2E7D32);
            | -fx-text-fill: white;
            |""".stripMargin
      }
    }

    val whiteField = new TextField() {
      promptText = "Enter white player name"
      text = "White"
      style = """
        | -fx-font-size: 14px;
        | -fx-padding: 10px;
        | -fx-border-radius: 5px;
        | -fx-border-color: #ccc;
        |""".stripMargin
    }

    val blackField = new TextField() {
      promptText = "Enter black player name"
      text = "Black"
      style = """
        | -fx-font-size: 14px;
        | -fx-padding: 10px;
        | -fx-border-radius: 5px;
        | -fx-border-color: #ccc;
        |""".stripMargin
    }

    val startButton = styledButton("Start Game", () => {
      onStartGame(whiteField.text.value, blackField.text.value)
    })

    val exitButton = styledButton("Exit", onExit)
    exitButton.style = 
      """
        | -fx-font-size: 16px;
        | -fx-font-weight: bold;
        | -fx-background-radius: 10;
        | -fx-padding: 15 20 15 20;
        | -fx-background-color: linear-gradient(#f44336, #d32f2f);
        | -fx-text-fill: white;
        |""".stripMargin

    val setupPanel = new VBox {
      spacing = 20
      padding = Insets(40)
      style = """
        | -fx-background-color: linear-gradient(to bottom, #f5f5f5, #e8e8e8);
        | -fx-border-radius: 15px;
        | -fx-border-color: #ddd;
        |""".stripMargin
      children = Seq(
        new scalafx.scene.text.Text("Chess Game Setup") {
          style = """
            | -fx-font-size: 24px;
            | -fx-font-weight: bold;
            | -fx-fill: #333;
            |""".stripMargin
        },
        new scalafx.scene.text.Text("Enter player names:") {
          style = """
            | -fx-font-size: 16px;
            | -fx-fill: #666;
            |""".stripMargin
        },
        new scalafx.scene.layout.VBox(10) {
          children = Seq(
            new scalafx.scene.text.Text("White Player:") {
              style = """
                | -fx-font-size: 14px;
                | -fx-font-weight: bold;
                | -fx-fill: #333;
                |""".stripMargin
            },
            whiteField
          )
        },
        new scalafx.scene.layout.VBox(10) {
          children = Seq(
            new scalafx.scene.text.Text("Black Player:") {
              style = """
                | -fx-font-size: 14px;
                | -fx-font-weight: bold;
                | -fx-fill: #333;
                |""".stripMargin
            },
            blackField
          )
        },
        new scalafx.scene.layout.HBox(15) {
          children = Seq(startButton, exitButton)
        }
      )
    }

    new BorderPane {
      center = setupPanel
      style = """
        | -fx-background-color: linear-gradient(45deg, #2196F3, #4CAF50);
        |""".stripMargin
    }
  }
}
