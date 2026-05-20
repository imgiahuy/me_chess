package tui

/** Enhanced TUI entry point with improved game loop.
 *
 * This version uses GameLoopV2 which includes:
 * - Better game status display (check, checkmate, stalemate)
 * - Move history viewing
 * - Improved error messages
 * - Enhanced help text
 */
object TuiEntry {
  def main(args: Array[String]): Unit = {
    val gameController = new controller.GameController
    GameLoop(gameController).start()
  }
}
