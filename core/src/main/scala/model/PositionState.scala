package model

import java.time.LocalDate

case class PositionState(
  board: Board,
  turn: Color,
  moveHistory: List[Move],
  whitePlayer: Player,
  blackPlayer: Player,
  creationDate: LocalDate = LocalDate.now(),
  id: Option[String] = None,
  // Time control
  timeControl: Option[TimeControl] = None,
  whiteTime: Option[PlayerTime] = None,
  blackTime: Option[PlayerTime] = None,
  // Draw conditions tracking
  halfmovesSinceLastCaptureOrPawn: Int = 0,  // For fifty-move rule
  positionHistory: List[Board] = List.empty,  // For threefold repetition
  // Game status
  hasWhiteResigned: Boolean = false,
  hasBlackResigned: Boolean = false,
  gameResult: GameResult = Ongoing
)