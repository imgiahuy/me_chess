package formatter

import model.{Move, Promotion, CastlingKingSide, CastlingQueenSide, EnPassant, Queen, Rook, Bishop, Knight}

/** Formats moves to UCI (Universal Chess Interface) notation.
 *
 * UCI format: "e2e4" (source square + destination square in algebraic notation)
 * Example: e2e4, g1f3, e7e8q (with promotion)
 * Castling: 0-0 (kingside), 0-0-0 (queenside)
 */
object UciFormatter {

  /** Converts a single move to UCI notation.
    *
    * @param move The move to format
    * @return UCI string (e.g., "e2e4", "e7e8q", "0-0")
    * @throws Exception if move positions are invalid
    */
  def uciFormatter(move: Move): String = {
    // Handle special moves
    move.specialMove match {
      case Some(CastlingKingSide) =>
        s"${squareToAlgebraic(move.from)}${squareToAlgebraic(move.to)}"
      case Some(CastlingQueenSide) =>
        s"${squareToAlgebraic(move.from)}${squareToAlgebraic(move.to)}"
      case Some(Promotion(promotion)) =>
        val from = move.from
        val to = move.to
        if (from.isValid && to.isValid) {
          val promoChar = promotion match {
            case Queen => 'q'
            case Rook => 'r'
            case Bishop => 'b'
            case Knight => 'n'
            case _ => 'q'
          }
          s"${squareToAlgebraic(from)}${squareToAlgebraic(to)}$promoChar"
        } else {
          throw new Exception(s"Invalid move positions: from=$from, to=$to")
        }
      case Some(EnPassant) =>
        val from = move.from
        val to = move.to
        if (from.isValid && to.isValid) {
          s"${squareToAlgebraic(from)}${squareToAlgebraic(to)}"
        } else {
          throw new Exception(s"Invalid move positions: from=$from, to=$to")
        }
      case None =>
        val from = move.from
        val to = move.to
        if (from.isValid && to.isValid) {
          s"${squareToAlgebraic(from)}${squareToAlgebraic(to)}"
        } else {
          throw new Exception(s"Invalid move positions: from=$from, to=$to")
        }
    }
  }

  /** Alias for uciFormatter for convenience */
  def moveToUci(move: Move): String = uciFormatter(move)

  /** Converts a list of moves to UCI notation, space-separated.
    *
    * @param moveList The list of moves to format
    * @return Space-separated UCI moves (e.g., "e2e4 g1f3 e7e5")
    */
  def uciListFormatter(moveList: List[Move]): String = {
    moveList.map(uciFormatter).mkString(" ")
  }

  /** Converts a Position to algebraic notation.
    *
    * @param pos The position to convert
    * @return Two-character algebraic notation (e.g., "e4", "a1")
    */
  private def squareToAlgebraic(pos: model.Position): String = {
    s"${('a' + pos.col).toChar}${pos.row + 1}"
  }
}
