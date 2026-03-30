package chess.domain

/** The complete, immutable state of a chess game at one point in time.
 *
 * @param board       current piece positions
 * @param currentTurn which colour must move next
 * @param moveHistory all moves made so far, most recent first
 *
 * `applyMove` is the single state-transition function; it returns a *new*
 * GameState without mutating anything.
 */
case class GameState(
                      board:       Board,
                      currentTurn: Color,
                      moveHistory: List[Move]
                    ) {

  /** Applies `move` and advances the turn; no legality check here. */
  def applyMove(move: Move): GameState =
    GameState(
      board       = board.withMove(move.from, move.to),
      currentTurn = currentTurn.opposite,
      moveHistory = move :: moveHistory
    )

  /** The most recent move, or None if the game just started. */
  def lastMove: Option[Move] = moveHistory.headOption

  /** Total half-moves (plies) played. */
  def totalMoves: Int = moveHistory.size
}

object GameState {
  /** Standard chess starting position with White to move. */
  def initial: GameState = GameState(Board.initial, White, List.empty)
}