package tui

object TuiEntry {
  def main(args: Array[String]): Unit = {
    val gameController = new controller.GameController
    GameLoop(gameController).start()
  }
}