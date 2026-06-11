package gui

import controller.{GameController, GameControllerInterface}
import gui.GuiEntry.stage
import scalafx.application.JFXApp3
import scalafx.scene.Scene

object GuiEntry extends JFXApp3 {

  override def start(): Unit = {

    val gameController: GameControllerInterface = new GameController()
    val controller = new GuiController(gameController)

    controller.start() // initialize board

    stage = new JFXApp3.PrimaryStage {
      title = "Scala Chess GUI"
      width = 1000
      height = 800
      scene = new Scene {
        root = controller.root
      }
    }
  }
}