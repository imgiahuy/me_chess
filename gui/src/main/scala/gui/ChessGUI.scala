package gui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.layout.{GridPane, VBox}
import scalafx.scene.control.{Button, Label}
import scalafx.geometry.Insets
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.Text
import scalafx.Includes._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import play.api.libs.json._

object ChessGUI extends JFXApp3 {

  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "chess-gui")

  implicit val ec: ExecutionContext =
    system.executionContext

  val client = new ChessClient("http://localhost:8080")

  var gameId: Option[String] = None
  var selectedFrom: Option[String] = None

  val squares = Array.ofDim[Text](8, 8)

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
  def renderBoard(grid: GridPane, board: JsValue, status: Label): Unit = {

    grid.children.clear()

    for (r <- 0 until 8; c <- 0 until 8) {

      val piece = (board(r)(c)).asOpt[String].getOrElse("")

      val bg = new Rectangle {
        width = 60
        height = 60
        fill = if ((r + c) % 2 == 0) Color.Beige else Color.Sienna
      }

      val text = new Text {
        this.text = pieceToUnicode(piece)
        style = "-fx-font-size: 28px;"
      }

      squares(r)(c) = text

      val cell = new VBox {
        children = Seq(bg, text)
        alignment = scalafx.geometry.Pos.Center

        onMouseClicked = _ => handleClick(r, c, status)
      }

      grid.add(cell, c, r)
    }
  }

  // -------------------------
  // CLICK LOGIC
  // -------------------------
  def handleClick(r: Int, c: Int, status: Label): Unit = {
    val sq = coordToSquare(r, c)

    selectedFrom match {
      case None =>
        selectedFrom = Some(sq)
        status.text = s"From: $sq"

      case Some(from) =>
        selectedFrom = None
        makeMove(from, sq, status)
    }
  }

  // -------------------------
  // API
  // -------------------------
  def createGame(status: Label, grid: GridPane): Unit = {
    status.text = "Creating game..."

    client.createGame().onComplete {
      case Success(id) =>
        gameId = Some(id)
        status.text = s"Game: $id"
        refresh(status, grid)

      case Failure(ex) =>
        status.text = ex.getMessage
    }
  }

  def refresh(status: Label, grid: GridPane): Unit = {
    gameId match {

      case Some(id) =>
        client.getGame(id).onComplete {
          case Success(game) =>
            status.text =
              s"Turn: ${game.currentTurn} | Over: ${game.isGameOver}"

            renderBoard(grid, game.board, status)

          case Failure(ex) =>
            status.text = ex.getMessage
        }

      case None =>
        status.text = "No game"
    }
  }

  def makeMove(from: String, to: String, status: Label): Unit = {
    gameId match {

      case Some(id) =>
        client.makeMove(id, from, to).onComplete {
          case Success(game) =>
            status.text =
              s"Turn: ${game.currentTurn} | Over: ${game.isGameOver}"

          case Failure(ex) =>
            status.text = ex.getMessage
        }

      case None =>
        status.text = "No game"
    }
  }

  // -------------------------
  // START UI
  // -------------------------
  override def start(): Unit = {

    val status = new Label("Ready")

    val boardGrid = new GridPane {
      hgap = 0
      vgap = 0
      padding = Insets(10)
    }

    val newGameBtn = new Button("New Game") {
      onAction = _ => createGame(status, boardGrid)
    }

    val refreshBtn = new Button("Refresh") {
      onAction = _ => refresh(status, boardGrid)
    }

    val root = new VBox {
      spacing = 10
      padding = Insets(10)

      children = Seq(
        newGameBtn,
        refreshBtn,
        boardGrid,
        status
      )
    }

    stage = new JFXApp3.PrimaryStage {
      title = "Chess GUI (Upgraded)"
      scene = new Scene(600, 650) {
        val rootPane = new VBox {
          spacing = 10
          padding = Insets(10)

          children = Seq(
            newGameBtn,
            refreshBtn,
            boardGrid,
            status
          )
        }

        this.root = rootPane
      }
    }
  }
}