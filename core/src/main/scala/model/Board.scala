package model

import scala.util.{Success, Try}

/** An immutable snapshot of piece positions. */
case class Board(squares: Map[Position, Piece]) {

  /** Looks up the piece at a square; None means empty. */
  def pieceAt(pos: Position): Option[Piece] = squares.get(pos)

  /** Returns true when no piece occupies the given square. */
  def isEmpty(pos: Position): Boolean = !squares.contains(pos)

  /** All (position → piece) pairs on this board. */
  def allPieces: Seq[(Position, Piece)] = squares.toSeq

  /** All pieces belonging to `color`. */
  def piecesOf(color: Color): Seq[(Position, Piece)] =
    allPieces.filter { case (_, p) => p.color == color }

  /** The set of colours that still have a king on the board. */
  def kingsAlive: Set[Color] =
    squares.values.collect { case p if p.pieceType == King => p.color }.toSet
}