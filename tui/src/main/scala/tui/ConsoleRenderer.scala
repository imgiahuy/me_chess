package tui

import model.{Board, Position, PositionState}
import service.GameService

/** Converts domain objects to displayable strings.
 *
 * Every function here is pure (String → String or Domain → String).
 * No println, no StdIn — all I/O is the caller's responsibility.
 *
 * Board display convention:
 *   - White pieces: upper-case  (K Q R B N P)
 *   - Black pieces: lower-case  (k q r b n p)
 *   - Light squares: ' '
 *   - Dark  squares: '.'
 *   - Rank 8 is at the top (Black side), Rank 1 at the bottom (White side)
 */
object ConsoleRenderer {
  
  private val LightEmpty = ' '
  private val DarkEmpty  = '.'

  // ── Board ────────────────────────────────────────────────────────────────────

  /** Renders the chess board with coordinates and piece positions. */
  def renderBoard(board: Board): String = {
    val header = "  a b c d e f g h"
    val rows   = (7 to 0 by -1).map(renderRank(board))
    (header +: rows).mkString("\n")
  }

  /** Renders one rank (row) as "N cell cell …" where N is the rank number. */
  private def renderRank(board: Board)(row: Int): String = {
    val cells = (0 until 8).map { col =>
      board.pieceAt(Position(col, row))
        .fold(if ((row + col) % 2 == 0) LightEmpty.toString else DarkEmpty.toString)(
          _.symbol.toString
        )
    }
    s"${row + 1} ${cells.mkString(" ")}"
  }

  // ── Game state ───────────────────────────────────────────────────────────────

  /** Renders the current game state including board, turn, and game status. */
  def renderGameState(state: PositionState, whiteTimeMs: Option[Long] = None, blackTimeMs: Option[Long] = None): String = {
    val board = renderBoard(state.board)
    val turn = s"Turn: ${state.turn}"
    val status = renderGameStatus(state)
    val clocks = renderClocks(state, whiteTimeMs, blackTimeMs)
    Seq(board, turn, clocks, status).mkString("\n")
  }

  /** Renders the remaining time for both players. */
  def renderClocks(state: PositionState, whiteTimeMs: Option[Long], blackTimeMs: Option[Long]): String = {
    val whiteTime = whiteTimeMs.orElse(state.whiteTime.map(_.getCurrentTime)).map(formatTime).getOrElse("--:--")
    val blackTime = blackTimeMs.orElse(state.blackTime.map(_.getCurrentTime)).map(formatTime).getOrElse("--:--")
    s"White: $whiteTime | Black: $blackTime"
  }

  /** Formats milliseconds to MM:SS format. */
  private def formatTime(ms: Long): String = {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    f"$minutes%02d:$seconds%02d"
  }

  /** Renders the current game status (check, checkmate, stalemate, normal). */
  def renderGameStatus(state: PositionState): String = {
    import model.{Ongoing, Checkmate, Draw, Resignation, TimeOut}
    state.gameResult match {
      case Checkmate(winner)   => s"CHECKMATE! $winner wins!"
      case Draw(reason)        => s"DRAW: $reason"
      case Resignation(winner) => s"RESIGNATION! $winner wins!"
      case TimeOut(winner)     => s"TIME OUT! $winner wins!"
      case Ongoing =>
        if GameService.isKingInCheck(state.board, state.turn) then
          s"CHECK! ${state.turn} is in check."
        else
          "Game in progress"
    }
  }

  // ── Outcome messages ─────────────────────────────────────────────────────────

  /** Formats the outcome of the current game state. */
  def renderOutcome(state: PositionState): String = {
    import model.{Ongoing, Checkmate, Draw, Resignation, TimeOut}
    state.gameResult match {
      case Checkmate(winner)   => s"CHECKMATE! $winner wins!"
      case Draw(reason)        => s"DRAW: $reason"
      case Resignation(winner) => s"RESIGNATION! $winner wins!"
      case TimeOut(winner)     => s"TIME OUT! $winner wins!"
      case Ongoing             => "Game over!"
    }
  }

  def renderError(msg: String): String = s"[ERROR] $msg"
  def renderSuccess(msg: String): String = s"[OK] $msg"

  // ── Move history ─────────────────────────────────────────────────────────────

  /** Renders the move history in a compact format. */
  def renderMoveHistory(state: PositionState): String = {
    if state.moveHistory.isEmpty then
      "No moves yet."
    else
      val moves = state.moveHistory.zipWithIndex.map { case (move, idx) =>
        val moveNum = idx / 2 + 1
        val notation = s"${('a' + move.from.col).toChar}${move.from.row + 1}-${('a' + move.to.col).toChar}${move.to.row + 1}"
        if idx % 2 == 0 then s"$moveNum. $notation" else notation
      }
      moves.grouped(6).map(_.mkString(" ")).mkString("\n")
  }

  // ── Help text ────────────────────────────────────────────────────────────────
  
  /** Displays help information and available commands. */
  val renderHelp: String =
    """|+============================================================+
       ||                    Scala Chess                            |
       |+============================================================+
       || COMMANDS:                                                 |
       ||   <move>    Make a move (UCI notation)                   |
       ||             Examples: e2e4, g1f3, e7e8q (promotion)     |
       ||             Castling: 0-0 (kingside), 0-0-0 (queenside) |
       ||   board     Redisplay the board                         |
       ||   moves     Show move history                           |
       ||   status    Show game status (check, checkmate, etc.)   |
       ||   help      Show this help message                      |
       ||   save      Save the game to 'savegame.txt'             |
       ||   load      Load the game from 'savegame.txt'           |
       ||   export pgn Export game to PGN file ('game.pgn')       |
       ||   quit      Exit the game                               |
       |+============================================================+
       || PIECES:                                                   |
       ||   K/k King   Q/q Queen  R/r Rook  B/b Bishop            |
       ||   N/n Knight P/p Pawn                                    |
       ||   Uppercase = White  |  Lowercase = Black               |
       |+============================================================+
       || BOARD:                                                    |
       ||   Light squares: ' '  |  Dark squares: '.'              |
       ||   Rank 8 (top) = Black side  |  Rank 1 (bottom) = White |
       |+============================================================+""".stripMargin
}
