package chess.presentation.gui

import domain.model
import chess.application.GameService
import chess.infrastructure.GameStateFEN
import domain.engine.GameState
import presentation.parser.MoveParser
import scalafx.scene.layout.BorderPane

import java.nio.file.{Files, Paths}

class GuiController {

  private var state: GameState = GameState.initial
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

        val result = for {
          move <- MoveParser.parse(moveStr).toRight("Invalid move")
          newState <- GameService.applyMove(state, move)
        } yield newState

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
      val data = GameStateFEN.save(state)
      val path = Paths.get("savegame.txt")
      Files.write(path, data.getBytes)
      println(s"Game saved to $path")
    } catch {
      case e: Exception =>
        println(s"Error saving: ${e.getMessage}")
    }
  }

  private def handleLoad(): Unit = {
    try {
      val path = Paths.get("savegame.txt")
      val content = new String(Files.readAllBytes(path))
      state = GameStateFEN.load(content)
      selected = None
      println(s"Game loaded from $path")
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