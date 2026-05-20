package model

import java.time.LocalDate

case class PositionState(
  board: Board, 
  turn: Color, 
  moveHistory: List[Move],
  whitePlayer: Player,
  blackPlayer: Player,
  creationDate: LocalDate = LocalDate.now()
)