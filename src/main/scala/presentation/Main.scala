package presentation

import chess.presentation.gui.GuiController
import chess.presentation.tui.GameLoop

/** Application entry point.
 *
 * Intentionally thin — all logic lives in the layers below.
 */
object Main extends App {
  GameLoop.start()
}