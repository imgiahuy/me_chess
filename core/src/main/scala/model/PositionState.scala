package model

import java.time.LocalDate

/** Domain model for chess position state.
  * TODO: This still contains application concerns (id, creationDate, timeControl, gameResult, etc.)
  * These should be moved to GameState in a future refactoring.
  */
case class PositionState(
  board: Board,
  turn: Color,
  moveHistory: List[Move],
  whitePlayer: Player,
  blackPlayer: Player,
  creationDate: LocalDate = LocalDate.now(),
  id: Option[String] = None,
  // Time control (application concern - should be in GameState)
  timeControl: Option[TimeControl] = None,
  whiteTime: Option[PlayerTime] = None,
  blackTime: Option[PlayerTime] = None,
  // Draw conditions tracking (domain logic for fifty-move and threefold repetition rules)
  halfmovesSinceLastCaptureOrPawn: Int = 0,
  positionHistory: List[Board] = List.empty,
  // Game status (application concern - should be in GameState)
  hasWhiteResigned: Boolean = false,
  hasBlackResigned: Boolean = false,
  gameResult: GameResult = Ongoing,
  // Pause state (application concern - should be in GameState)
  isPaused: Boolean = false,
  pausedAt: Option[Long] = None
) {
  /** Domain method to check if the game is over based on kings being captured.
    * Note: This is a simplified check - full game over logic includes gameResult.
    */
  def isKingsAliveGameOver: Boolean = board.kingsAlive.size < 2

  /** Full game over check (includes application concerns) */
  def isGameOver: Boolean = isKingsAliveGameOver || gameResult != Ongoing
}