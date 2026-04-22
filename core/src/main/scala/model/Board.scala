package domain.model

import scala.util.{Success, Try}

/** An immutable snapshot of piece positions. */
case class Board(squares: Map[Position, Piece]) {

  /** Looks up the piece at a square; None means empty. */
  def pieceAt(pos: Position): Option[Piece] = squares.get(pos)

  /** Returns true when no piece occupies the given square. */
  def isEmpty(pos: Position): Boolean = !squares.contains(pos)

  /** Moves the piece from `from` to `to`, safely.
   *
   * Returns Success(newBoard) if move is valid,
   * Failure with a message if `from` square is empty.
   */
  def withMove(from: Position, to: Position): Try[Board] =
    pieceAt(from) match {
      case Some(piece) =>
        // normal move or capture
        val newSquares = squares - from + (to -> piece)
        Success(copy(squares = newSquares))
      case None =>
        // source empty → return same board
        Success(this)
    }

  /** All (position → piece) pairs on this board. */
  def allPieces: Seq[(Position, Piece)] = squares.toSeq

  /** All pieces belonging to `color`. */
  def piecesOf(color: Color): Seq[(Position, Piece)] =
    allPieces.filter { case (_, p) => p.color == color }

  /** The set of colours that still have a king on the board. */
  def kingsAlive: Set[Color] =
    squares.values.collect { case p if p.pieceType == King => p.color }.toSet
}

object Board {

  /** Standard back-rank order from a-file to h-file. */
  val backRankTypes: Seq[PieceType] =
    Seq(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)

  /** Builds the standard starting position. */
  def initial: Board = {
    val makeBackRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, row) => backRankTypes.zipWithIndex.map { case (pt, col) =>
        Position(col, row) -> Piece(color, pt)
      }

    val makePawnRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, row) => (0 until 8).map(col => Position(col, row) -> Piece(color, Pawn))

    Board(
      (makeBackRank(White, 0) ++
        makePawnRank(White, 1) ++
        makePawnRank(Black, 6) ++
        makeBackRank(Black, 7)).toMap
    )
  }

  /** An empty board — useful for tests and custom positions. */
  val empty: Board = Board(Map.empty)
}