package chess.domain

/** An immutable snapshot of piece positions.
 *
 * Internally a `Map[Position, Piece]`; absent keys mean empty squares.
 * All mutation methods return a *new* Board — the original is never changed.
 */
case class Board(squares: Map[Position, Piece]) {

  /** Looks up the piece at a square; None means empty. */
  def pieceAt(pos: Position): Option[Piece] = squares.get(pos)

  /** Returns true when no piece occupies the given square. */
  def isEmpty(pos: Position): Boolean = !squares.contains(pos)

  /** Moves the piece from `from` to `to`, replacing any captured piece.
   *
   * If `from` is empty this board is returned unchanged (idempotent).
   * Uses function chaining: fold over the Option, then Map operations.
   */
  def withMove(from: Position, to: Position): Board =
    pieceAt(from).fold(this) { piece =>
      Board((squares - from) + (to -> piece))
    }

  /** All (position → piece) pairs on this board. */
  def allPieces: Seq[(Position, Piece)] = squares.toSeq

  /** All pieces belonging to `color`. */
  def piecesOf(color: Color): Seq[(Position, Piece)] =
    allPieces.filter { case (_, p) => p.color == color }

  /** The set of colours that still have a king on the board. */
  def kingsAlive: Set[Color] =
    squares.values
      .withFilter(_.pieceType == King)
      .map(_.color)
      .toSet
}

object Board {

  /** The standard back-rank order from a-file to h-file. */
  val backRankTypes: Seq[PieceType] =
    Seq(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)

  /** Builds the standard starting position using functional combinators. */
  def initial: Board = {
    val makeBackRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, baseRow) =>
        backRankTypes.zipWithIndex.map { case (pt, col) =>
          Position(col, baseRow) -> Piece(color, pt)
        }

    val makePawnRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, pawnRow) =>
        (0 until 8).map(col => Position(col, pawnRow) -> Piece(color, Pawn))

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