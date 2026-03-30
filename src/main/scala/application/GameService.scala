package chess.application

import chess.domain._

/** Pure game-rule functions.
 *
 * All functions are referentially transparent:
 *   - No mutation
 *   - No I/O
 *   - Same input always produces the same output
 *
 * `applyMove` is the primary entry point from the UI layer.
 */
object GameService {

  // ── Move application ────────────────────────────────────────────────────────

  /** Validates and applies `move` to `state`.
   *
   * Returns Right(newState) on success, Left(reason) on failure.
   * Uses for-comprehension over Either for clean chained validation.
   */
  def applyMove(state: GameState, move: Move): Either[String, GameState] =
    validate(state, move).map(_ => state.applyMove(move))

  /** Runs all precondition checks in order, short-circuiting on first failure.
   *
   * Checks performed (no piece-movement rules yet — to be extended):
   *   1. Both squares are within the 8×8 board
   *   2. Source and destination are different squares
   *   3. A piece exists on the source square
   *   4. That piece belongs to the player whose turn it is
   */
  def validate(state: GameState, move: Move): Either[String, Unit] =
    for {
      _ <- Either.cond(
        move.from.isValid && move.to.isValid,
        (),
        s"Square out of board bounds"
      )
      _ <- Either.cond(
        move.from != move.to,
        (),
        "Source and destination are the same square"
      )
      piece <- state.board
        .pieceAt(move.from)
        .toRight(s"No piece at ${move.from.toAlgebraic}")
      _ <- Either.cond(
        piece.color == state.currentTurn,
        (),
        s"It is ${state.currentTurn}'s turn, not ${piece.color}'s"
      )
    } yield ()

  // ── Game-over detection ──────────────────────────────────────────────────────

  /** The game is over when at least one king has been captured. */
  def isGameOver(state: GameState): Boolean =
    state.board.kingsAlive.size < 2

  /** Returns the winner if exactly one king remains; None if still ongoing
   * or if both kings were lost simultaneously (theoretical edge case).
   */
  def winner(state: GameState): Option[Color] =
    state.board.kingsAlive.toList match {
      case color :: Nil => Some(color)
      case _            => None
    }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /** True when the active player still has at least one piece on the board. */
  def currentPlayerHasPieces(state: GameState): Boolean =
    state.board.piecesOf(state.currentTurn).nonEmpty
}