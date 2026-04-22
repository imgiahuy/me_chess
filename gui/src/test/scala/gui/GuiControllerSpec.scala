package gui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}


class GuiControllerSpec extends AnyWordSpec with Matchers {

  Platform.startup(() => {})

  "GuiController" should {

    "initialize with default state and no selection" in {
      val controller = new GuiController()

      // Using reflection since fields are private
      val stateField = classOf[GuiController].getDeclaredField("state")
      stateField.setAccessible(true)

      val selectedField = classOf[GuiController].getDeclaredField("selected")
      selectedField.setAccessible(true)

      stateField.get(controller) should not be null
      selectedField.get(controller) shouldBe None
    }

    "set center when start is called" in {
      val controller = new GuiController()

      controller.start()

      controller.root.center should not be null
    }

    "select a piece on first click" in {
      val controller = new GuiController()

      val method = classOf[GuiController].getDeclaredMethod("handleClick", classOf[Int], classOf[Int])
      method.setAccessible(true)

      method.invoke(controller, Int.box(0), Int.box(1))

      val selectedField = classOf[GuiController].getDeclaredField("selected")
      selectedField.setAccessible(true)

      selectedField.get(controller) shouldBe Some((0, 1))
    }

    "reset selection after second click" in {
      val controller = new GuiController()

      val method = classOf[GuiController].getDeclaredMethod("handleClick", classOf[Int], classOf[Int])
      method.setAccessible(true)

      // First click
      method.invoke(controller, Int.box(0), Int.box(1))
      // Second click
      method.invoke(controller, Int.box(0), Int.box(2))

      val selectedField = classOf[GuiController].getDeclaredField("selected")
      selectedField.setAccessible(true)

      selectedField.get(controller) shouldBe None
    }

    "save game to file" in {
      val controller = new GuiController()

      val method = classOf[GuiController].getDeclaredMethod("handleSave")
      method.setAccessible(true)

      method.invoke(controller)

      val path = Paths.get("savegame.txt")
      Files.exists(path) shouldBe true
    }

    "load game from file" in {
      val controller = new GuiController()

      // Ensure file exists first
      val saveMethod = classOf[GuiController].getDeclaredMethod("handleSave")
      saveMethod.setAccessible(true)
      saveMethod.invoke(controller)

      val loadMethod = classOf[GuiController].getDeclaredMethod("handleLoad")
      loadMethod.setAccessible(true)

      noException should be thrownBy loadMethod.invoke(controller)
    }

    "convert coordinates to algebraic notation correctly" in {
      val controller = new GuiController()

      val method = classOf[GuiController].getDeclaredMethod("toAlg", classOf[Int], classOf[Int])
      method.setAccessible(true)

      method.invoke(controller, Int.box(0), Int.box(0)) shouldBe "a1"
      method.invoke(controller, Int.box(7), Int.box(7)) shouldBe "h8"
    }
  }
}