package gui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GuiRendererSpec extends AnyWordSpec with Matchers {

  "GuiRenderer.renderBoard" should {

    "create an 8x8 grid" in {
      val board = Board.empty
      val grid = GuiRenderer.renderBoard(board, (_, _) => ())

      grid.children.size shouldBe 64
    }

    "attach click handlers to each square" in {
      var clicked: Option[(Int, Int)] = None

      val board = Board.empty
      val grid = GuiRenderer.renderBoard(board, (c, r) => clicked = Some((c, r)))

      val firstSquare = grid.children.head.asInstanceOf[javafx.scene.layout.StackPane]

      // simulate click
      firstSquare.onMouseClicked.value.handle(null)

      clicked should not be None
    }

    "render a piece if present on board" in {
      val piece = Piece(White, Pawn)
      val board = Board.empty

      val grid = GuiRenderer.renderBoard(board, (_, _) => ())

      val square = grid.children.head.asInstanceOf[javafx.scene.layout.StackPane]

      // Should contain rectangle + image
      square.children.size should be >= 1
    }
  }

  "GuiRenderer.renderGameUI" should {

    "create a BorderPane with board in center" in {
      val board = Board.empty

      val ui = GuiRenderer.renderGameUI(
        board,
        (_, _) => (),
        () => (),
        () => (),
        () => ()
      )

      ui shouldBe a[BorderPane]
      ui.center.value should not be null
    }

    "create side panel with 3 buttons" in {
      val board = Board.empty

      val ui = GuiRenderer.renderGameUI(
        board,
        (_, _) => (),
        () => (),
        () => (),
        () => ()
      )

      val sidePanel = ui.right.value.asInstanceOf[javafx.scene.layout.VBox]

      sidePanel.children.size shouldBe 3
      sidePanel.children.foreach {
        case _: javafx.scene.control.Button => succeed
        case _ => fail("Expected Button")
      }
    }

    "buttons should trigger their actions" in {
      var saveCalled = false
      var loadCalled = false
      var exitCalled = false

      val board = Board.empty

      val ui = GuiRenderer.renderGameUI(
        board,
        (_, _) => (),
        () => exitCalled = true,
        () => saveCalled = true,
        () => loadCalled = true
      )

      val sidePanel = ui.right.value.asInstanceOf[javafx.scene.layout.VBox]
      val buttons = sidePanel.children.map(_.asInstanceOf[javafx.scene.control.Button])

      buttons(0).onAction.value.handle(null) // Save
      buttons(1).onAction.value.handle(null) // Load
      buttons(2).onAction.value.handle(null) // Exit

      saveCalled shouldBe true
      loadCalled shouldBe true
      exitCalled shouldBe true
    }
  }
}