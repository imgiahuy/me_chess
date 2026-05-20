package formatter

import model.Move

/** Formats moves to UCI (Universal Chess Interface) notation.
 *
 * UCI format: "e2e4" (source square + destination square in algebraic notation)
 * Example: e2e4, g1f3, e7e8q (with promotion)
 */
object UciFormatter {

  /** Converts a single move to UCI notation.
   *
   * @param move The move to format
   * @return UCI string (e.g., "e2e4")
   * @throws Exception if move positions are invalid
   */
  def uciFormatter(move: Move): String = {
    val from = move.from
    val to = move.to
    if (from.isValid && to.isValid) {
      s"${squareToAlgebraic(from)}${squareToAlgebraic(to)}"
    } else {
      throw new Exception(s"Invalid move positions: from=$from, to=$to")
    }
  }

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
