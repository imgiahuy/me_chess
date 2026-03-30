package chess.presentation.tui

import chess.domain._
import chess.application.GameService

/** Converts domain objects to displayable strings.
 *
 * Every function here is pure (String → String or Domain → String).
 * No println, no StdIn — all I/O is the caller's responsibility.
 *
 * Board display convention:
 *   - White pieces: upper-case  (K Q R B N P)
 *   - Black pieces: lower-case  (k q r b n p)
 *   - Light squares: ' '
 *   - Dark  squares: ','
 *   - Rank 8 is at the top (Black side), Rank 1 at the bottom (White side)
 */
object ConsoleRenderer {

  private val LightEmpty = ' '
  private val DarkEmpty  = '.'

  // ── Board ────────────────────────────────────────────────────────────────────

  def renderBoard(board: Board): String = {
    val header = "  a b c d e f g h"
    val rows   = (7 to 0 by -1).map(renderRank(board))
    (header +: rows).mkString("\n")
  }

  /** Renders one rank (row) as "N cell cell …" where N is the rank number. */
  val renderRank: Board => Int => String =
    board =>
      row => {
        val cells = (0 until 8).map { col =>
          board.pieceAt(Position(col, row))
            .fold(if ((row + col) % 2 == 0) LightEmpty.toString else DarkEmpty.toString)(
              _.symbol.toString
            )
        }
        s"${row + 1} ${cells.mkString(" ")}"
      }

  // ── Game state ───────────────────────────────────────────────────────────────

  def renderGameState(state: GameState): String = {
    val lastMoveInfo = state.lastMove
      .map(m => s"Last move: ${m.toAlgebraic}")
      .getOrElse("No moves yet")

    Seq(
      renderBoard(state.board),
      s"Turn: ${state.currentTurn}  |  Moves played: ${state.totalMoves}",
      lastMoveInfo
    ).mkString("\n")
  }

  // ── Outcome messages ─────────────────────────────────────────────────────────

  def renderWinner(color: Color): String  = s"Game over! $color wins!"
  def renderDraw: String                  = "Game over! It's a draw!"
  def renderError(msg: String): String    = s"Error: $msg"

  /** Formats the outcome of the current game state. */
  def renderOutcome(state: GameState): String =
    GameService.winner(state)
      .map(renderWinner)
      .getOrElse(renderDraw)

  // ── Help text ────────────────────────────────────────────────────────────────
  val renderHelp: String =
    """|+----------------------------------+
       ||           Scala Chess            |
       |+----------------------------------+
       ||  <move>  Make a move             |
       ||          e.g. e2e4  e2 e4        |
       ||  board   Redisplay the board     |
       ||  help    Show this help          |
       ||  quit    Exit the game           |
       |+----------------------------------+
       ||  K/k King   Q/q Queen  R/r Rook  |
       ||  B/b Bishop N/n Knight P/p Pawn  |
       ||  uppercase=White lowercase=Black |
       |+----------------------------------+""".stripMargin
}