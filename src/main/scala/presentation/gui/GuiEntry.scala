package chess.presentation.gui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import chess.presentation.gui.GuiController

object GuiEntry extends JFXApp3 {

  override def start(): Unit = {

    val controller = new GuiController()
    controller.start() // initialize board

    stage = new JFXApp3.PrimaryStage {
      title = "Scala Chess GUI"
      width = 650
      height = 680
      scene = new Scene {
        root = controller.root
      }
    }
  }
}