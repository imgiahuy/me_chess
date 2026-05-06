import controller.GameController
import gui.GuiController
import scalafx.application.JFXApp3
import scalafx.scene.Scene

object GuiEntry extends JFXApp3 {

  override def start(): Unit = {

    val gameController = new GameController()
    val controller = new GuiController(gameController)

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