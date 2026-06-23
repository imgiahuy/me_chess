package model

import java.time.LocalDate

/** Application-level wrapper for PositionState with persistence, time control, and game status concerns.
  * This separates domain logic (PositionState) from application concerns (IDs, timestamps, time controls, game result).
  */
case class GameState(
  positionState: PositionState,
  id: Option[String] = None,
  creationDate: LocalDate = LocalDate.now(),
  timeControl: Option[TimeControl] = None,
  whiteTime: Option[PlayerTime] = None,
  blackTime: Option[PlayerTime] = None,
  // Game status (application concerns)
  hasWhiteResigned: Boolean = false,
  hasBlackResigned: Boolean = false,
  gameResult: GameResult = Ongoing,
  // Pause state (application concern)
  isPaused: Boolean = false,
  pausedAt: Option[Long] = None
) {
  /** Delegate to PositionState for domain operations */
  def board: Board = positionState.board
  def turn: Color = positionState.turn
  def moveHistory: List[Move] = positionState.moveHistory
  def whitePlayer: Player = positionState.whitePlayer
  def blackPlayer: Player = positionState.blackPlayer
  def halfmovesSinceLastCaptureOrPawn: Int = positionState.halfmovesSinceLastCaptureOrPawn
  def positionHistory: List[Board] = positionState.positionHistory

  /** Check if the game is over (combines domain and application logic) */
  def isGameOver: Boolean = positionState.isKingsAliveGameOver || gameResult != Ongoing

  /** Create a new GameState with updated PositionState */
  def withPositionState(newState: PositionState): GameState = 
    copy(positionState = newState)

  /** Create a new GameState with updated ID */
  def withId(newId: String): GameState = 
    copy(id = Some(newId))

  /** Create a new GameState with updated game result */
  def withGameResult(result: GameResult): GameState =
    copy(gameResult = result)

  /** Create a new GameState with updated pause state */
  def withPaused(paused: Boolean, at: Option[Long] = None): GameState =
    copy(isPaused = paused, pausedAt = at)
}
