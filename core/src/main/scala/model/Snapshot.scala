package model

case class Snapshot (board: Board, turn: Color, moveHistory: Seq[Move])