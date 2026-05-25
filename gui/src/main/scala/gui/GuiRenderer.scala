package gui

import scalafx.scene.layout.{BorderPane, GridPane, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.Text
import model.{Black, Board, Piece, Position, White}
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, TextField, TextArea, Label}
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.animation.{KeyFrame, KeyValue, Timeline}
import scalafx.util.Duration
import scalafx.event.ActionEvent
import scalafx.scene.text.Font

object GuiRenderer {

  private var boardGridPane: Option[GridPane] = None
  private val squareCache = scala.collection.mutable.Map[(Int, Int), StackPane]()

  private val PIECE_SYMBOLS: Map[(Char, Boolean), String] = Map(
    ('K', true) -> "♔", ('Q', true) -> "♕", ('R', true) -> "♖", ('B', true) -> "♗", ('N', true) -> "♘", ('P', true) -> "♙",
    ('k', false) -> "♚", ('q', false) -> "♛", ('r', false) -> "♜", ('b', false) -> "♝", ('n', false) -> "♞", ('p', false) -> "♟"
  )

  private def pieceSymbol(piece: Piece): Text = {
    val symbol = PIECE_SYMBOLS.getOrElse((piece.symbol, piece.color == White), piece.symbol.toString)
    new Text(symbol) {
      font = Font(48)
      fill = if (piece.color == White) Color.White else Color.Black
      style = "-fx-text-shadow: 0 0 3px rgba(0,0,0,0.8);"
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
          Color.web("#FFD700")  // Gold for selected
        } else if (isValidMove) {
          Color.web("#4CAF50")  // Green for valid moves
        } else if (isLight) {
          Color.web("#B58863")  // Light square (dark theme)
        } else {
          Color.web("#F0D9B5")  // Dark square (dark theme)
        }
      }

      square.children.add(rect)

      board.pieceAt(Position(col, row)).foreach { piece =>
        square.children.add(pieceSymbol(piece))
      }

      // Add hover effect
      square.onMouseEntered = _ => {
        rect.fill = if (isSelected) {
          Color.web("#FFC107")
        } else if (isValidMove) {
          Color.web("#66BB6A")
        } else if (isLight) {
          Color.web("#C9A86C")
        } else {
          Color.web("#E8C89A")
        }
      }

      square.onMouseExited = _ => {
        rect.fill = if (isSelected) {
          Color.web("#FFD700")
        } else if (isValidMove) {
          Color.web("#4CAF50")
        } else if (isLight) {
          Color.web("#B58863")
        } else {
          Color.web("#F0D9B5")
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
    // Animation with text symbols is simpler - just skip for now
    // Text-based pieces don't animate as smoothly as images
  }

  def renderGameUI(
                    board: Board,
                    onClick: (Int, Int) => Unit,
                    onExit: () => Unit,
                    onSave: () => Unit,
                    onLoad: () => Unit,
                    onExportPgn: () => Unit,
                    selected: Option[(Int, Int)],
                    validMoves: Set[(Int, Int)],
                    moveHistory: List[String],
                    notification: String
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
            | -fx-background-color: linear-gradient(#2196F3, #1976D2);
            | -fx-text-fill: white;
            |""".stripMargin

        onAction = _ => action()

        onMouseEntered = _ => style =
          """
            | -fx-font-size: 14px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 10 15 10 15;
            | -fx-background-color: linear-gradient(#42A5F5, #1E88E5);
            | -fx-text-fill: white;
            |""".stripMargin

        onMouseExited = _ => style =
          """
            | -fx-font-size: 14px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 10 15 10 15;
            | -fx-background-color: linear-gradient(#2196F3, #1976D2);
            | -fx-text-fill: white;
            |""".stripMargin
      }
    }

    val boardView = renderBoard(board, onClick, selected, validMoves)

    val saveButton = styledButton("Save", onSave)
    val loadButton = styledButton("Load", onLoad)
    val exportButton = styledButton("Export PGN", onExportPgn)
    val exitButton = styledButton("Exit", onExit)

    // Move history panel
    val historyLabel = new Label("Move History") {
      style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;"
    }
    
    val historyArea = new TextArea {
      prefWidth = 200
      prefHeight = 200
      editable = false
      text = moveHistory.mkString("\n")
      style = """
        | -fx-font-size: 12px;
        | -fx-font-family: monospace;
        | -fx-background-color: #2C2C2C;
        | -fx-text-fill: #E0E0E0;
        | -fx-control-inner-background: #2C2C2C;
        |""".stripMargin
    }
    
    // Notification panel
    val notificationLabel = new Label("Notifications") {
      style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;"
    }
    
    val notificationArea = new TextArea {
      prefWidth = 200
      prefHeight = 80
      editable = false
      text = notification
      style = """
        | -fx-font-size: 12px;
        | -fx-background-color: #2C2C2C;
        | -fx-text-fill: #E0E0E0;
        | -fx-control-inner-background: #2C2C2C;
        |""".stripMargin
    }

    val sidePanel = new VBox {
      spacing = 10
      padding = Insets(10)
      style = "-fx-background-color: #1E1E1E;"
      children = List(saveButton, loadButton, exportButton, exitButton, historyLabel, historyArea, notificationLabel, notificationArea)
    }

    new BorderPane {
      center = boardView
      right = sidePanel
      style = "-fx-background-color: #121212;"
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
            | -fx-background-color: linear-gradient(#2196F3, #1976D2);
            | -fx-text-fill: white;
            |""".stripMargin

        onAction = _ => action()

        onMouseEntered = _ => style =
          """
            | -fx-font-size: 16px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 15 20 15 20;
            | -fx-background-color: linear-gradient(#42A5F5, #1E88E5);
            | -fx-text-fill: white;
            |""".stripMargin

        onMouseExited = _ => style =
          """
            | -fx-font-size: 16px;
            | -fx-font-weight: bold;
            | -fx-background-radius: 10;
            | -fx-padding: 15 20 15 20;
            | -fx-background-color: linear-gradient(#2196F3, #1976D2);
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
        | -fx-border-color: #555;
        | -fx-background-color: #2C2C2C;
        | -fx-text-fill: white;
        |""".stripMargin
    }

    val blackField = new TextField() {
      promptText = "Enter black player name"
      text = "Black"
      style = """
        | -fx-font-size: 14px;
        | -fx-padding: 10px;
        | -fx-border-radius: 5px;
        | -fx-border-color: #555;
        | -fx-background-color: #2C2C2C;
        | -fx-text-fill: white;
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
        | -fx-background-color: linear-gradient(to bottom, #2C2C2C, #1E1E1E);
        | -fx-border-radius: 15px;
        | -fx-border-color: #444;
        |""".stripMargin
      children = Seq(
        new scalafx.scene.text.Text("Chess Game Setup") {
          style = """
            | -fx-font-size: 24px;
            | -fx-font-weight: bold;
            | -fx-fill: white;
            |""".stripMargin
        },
        new scalafx.scene.text.Text("Enter player names:") {
          style = """
            | -fx-font-size: 16px;
            | -fx-fill: #B0B0B0;
            |""".stripMargin
        },
        new scalafx.scene.layout.VBox(10) {
          children = Seq(
            new scalafx.scene.text.Text("White Player:") {
              style = """
                | -fx-font-size: 14px;
                | -fx-font-weight: bold;
                | -fx-fill: white;
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
                | -fx-fill: white;
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
        | -fx-background-color: linear-gradient(45deg, #1E1E1E, #2C2C2C);
        |""".stripMargin
    }
  }
}
