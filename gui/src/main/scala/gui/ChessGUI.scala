package gui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.layout.{GridPane, StackPane, VBox}
import scalafx.scene.control.{Button, Label}
import scalafx.geometry.Insets
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.Text
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import scalafx.application.Platform
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ChessGUI extends JFXApp3 {

  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "chess-gui")

  implicit val ec: ExecutionContext =
    system.executionContext

  val client = new ChessClient("http://localhost:8080")

  var gameId: Option[String] = None
  var selectedFrom: Option[String] = None

  // -------------------------
  // PIECE MAPPING
  // -------------------------
  def pieceToUnicode(p: String): String = p match {
    case "k" => "♚"
    case "q" => "♛"
    case "r" => "♜"
    case "b" => "♝"
    case "n" => "♞"
    case "p" => "♟"
    case "K" => "♔"
    case "Q" => "♕"
    case "R" => "♖"
    case "B" => "♗"
    case "N" => "♘"
    case "P" => "♙"
    case _   => ""
  }

  def coordToSquare(r: Int, c: Int): String =
    s"${('a' + c).toChar}${8 - r}"

  // -------------------------
  // BOARD RENDERING
  // -------------------------
  def addSquare(
                 grid: GridPane,
                 r: Int,
                 c: Int,
                 piece: String,
                 status: Label
               ): Unit = {

    val isLight = (r + c) % 2 == 0

    val square = new StackPane {
      prefWidth = 70
      prefHeight = 70

      children = Seq(
        new Rectangle {
          width = 70
          height = 70
          fill = if (isLight) Color.Beige else Color.Brown
        },
        new Text {
          text = piece
          style = "-fx-font-size: 32px;"
        }
      )

      onMouseClicked = _ => handleClick(r, c, status, grid)
    }

    grid.add(square, c, r)
  }

  def renderBoard(grid: GridPane, fen: String, status: Label): Unit = {
    grid.children.clear()

    val rows = fen.split("/")

    for (r <- 0 until 8) {
      var c = 0

      for (ch <- rows(r)) {

        if (ch.isDigit) {
          val empty = ch.asDigit
          for (_ <- 0 until empty) {
            addSquare(grid, r, c, "", status)
            c += 1
          }
        } else {
          val piece = pieceToUnicode(ch.toString)
          addSquare(grid, r, c, piece, status)
          c += 1
        }
      }
    }
    println("Rendering board...")
  }

  // -------------------------
  // CLICK LOGIC
  // -------------------------
  def handleClick(r: Int, c: Int, status: Label, grid: GridPane): Unit = {
    val sq = coordToSquare(r, c)

    selectedFrom match {
      case None =>
        selectedFrom = Some(sq)
        status.text = s"From: $sq"

      case Some(from) =>
        selectedFrom = None
        makeMove(from, sq, status, grid)
    }
  }

  // -------------------------
  // API
  // -------------------------
  def createGame(status: Label, grid: GridPane): Unit = {
    status.text = "Creating game..."

    client.createGame().onComplete {
      case Success(id) =>
        Platform.runLater{
          gameId = Some(id)
          status.text = s"Game: $id"
          refresh(status, grid)
        }

      case Failure(ex) =>
        Platform.runLater{
          status.text = ex.getMessage
        }
    }
  }

  def refresh(status: Label, grid: GridPane): Unit = {
    gameId match {

      case Some(id) =>
        client.getGame(id).onComplete {
          case Success(game) =>
            Platform.runLater{
              status.text =
                s"Turn: ${game.currentTurn} | Over: ${game.isGameOver}"
              renderBoard(grid, game.board, status)
            }

          case Failure(ex) =>
            Platform.runLater{
              status.text = ex.getMessage
            }
        }

      case None =>
        status.text = "No game"
    }
  }

  def makeMove(from: String, to: String, status: Label, grid: GridPane): Unit = {
    gameId match {

      case Some(id) =>
        client.makeMove(id, from, to).onComplete {
          case Success(game) =>
            Platform.runLater {
              status.text =
                s"Turn: ${game.currentTurn} | Over: ${game.isGameOver}"

              refresh(status, grid)   // ✅ clean and reliable
            }

          case Failure(ex) =>
            Platform.runLater {
              status.text = ex.getMessage
            }
        }

      case None =>
        status.text = "No game"
    }
  }

  def createGameUI(stage: JFXApp3.PrimaryStage): VBox = {
    val buttonStyle =
      """
    -fx-font-size: 16px;
    -fx-font-weight: bold;
    -fx-text-fill: white;
    -fx-background-color: linear-gradient(#4a90e2, #357ABD);
    -fx-background-radius: 12;
    -fx-padding: 10 20 10 20;
  """

    val status = new Label("Ready")

    val boardGrid = new GridPane {
      hgap = 0
      vgap = 0
      padding = Insets(10)
    }

    val newGameBtn = new Button("New") {
      prefWidth = 100
      style = buttonStyle
      onAction = _ => createGame(status, boardGrid)
    }

    val refreshBtn = new Button("Refresh") {
      prefWidth = 100
      style = buttonStyle
      onAction = _ => refresh(status, boardGrid)
    }

    new VBox {
      spacing = 10
      padding = Insets(10)
      style = "-fx-background-color: #1e1e1e;"


      children = Seq(
        newGameBtn,
        refreshBtn,
        boardGrid,
        status
      )
    }
  }

  def createMenuUI(stage: JFXApp3.PrimaryStage): VBox = {

    val buttonStyle =
      """
    -fx-font-size: 16px;
    -fx-font-weight: bold;
    -fx-text-fill: white;
    -fx-background-color: linear-gradient(#4a90e2, #357ABD);
    -fx-background-radius: 12;
    -fx-padding: 10 20 10 20;
  """

    val hoverStyle =
      """
    -fx-background-color: linear-gradient(#5aa3f5, #2f6fb3);
  """

    val playBtn = new Button("Play") {
      prefWidth = 200
      style = buttonStyle
      onMouseEntered = _ => style = buttonStyle + hoverStyle
      onMouseExited = _ => style = buttonStyle
      onAction = _ => {
        stage.scene = new Scene(600, 1000) {
          root = createGameUI(stage)
        }
      }
    }

    val continueBtn = new Button("Continue") {
      prefWidth = 200
      style = buttonStyle
      onMouseEntered = _ => style = buttonStyle + hoverStyle
      onMouseExited = _ => style = buttonStyle
    }

    val settingsBtn = new Button("Settings") {
      prefWidth = 200
      style = buttonStyle
      onMouseEntered = _ => style = buttonStyle + hoverStyle
      onMouseExited = _ => style = buttonStyle
    }

    val exitBtn = new Button("Exit") {
      prefWidth = 200
      style = buttonStyle
      onMouseEntered = _ => style = buttonStyle + hoverStyle
      onMouseExited = _ => style = buttonStyle
      onAction = _ => {
        Platform.exit()
      }
    }

    new VBox {
      spacing = 20
      padding = Insets(50)
      style = "-fx-alignment: center;" +
        "-fx-background-color: #1e1e1e;"

      children = Seq(
        new Label("Chess Game") {
          style = "-fx-font-size: 28px;"},
        playBtn,
        continueBtn,
        settingsBtn,
        exitBtn
      )
    }
  }

  // -------------------------
  // START UI
  // -------------------------
  override def start(): Unit = {

    stage = new JFXApp3.PrimaryStage {
      title = "Chess GUI"

      val primaryStageRef = this // Capture reference to primary stage for later use

      scene = new Scene(600, 1000) {
        root = createMenuUI(primaryStageRef)
      }
    }
  }
}