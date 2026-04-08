package domain.model

/** A board coordinate (col 0–7, row 0–7).
 *
 * col 0 = file 'a', col 7 = file 'h'
 * row 0 = rank  1,  row 7 = rank  8
 *
 * Immutable value object — equality and hashing are structural (case class).
 */
case class Position(col: Int, row: Int) {

  /** Returns true only when both coordinates are inside the 8×8 board. */
  def isValid: Boolean = col >= 0 && col < 8 && row >= 0 && row < 8

  /** Converts to standard algebraic notation, e.g. Position(4,1) → "e2". */
  def toAlgebraic: String = s"${('a' + col).toChar}${row + 1}"
}

object Position {

  /** Parses a two-character algebraic string ("a1"–"h8") into a Position.
   *
   * Returns None for any input that is not a valid square identifier.
   * Accepts both upper- and lower-case file letters.
   */
  def fromAlgebraic(s: String): Option[Position] =
    if (s.length == 2) {
      val col = s(0).toLower - 'a'
      val row = s(1) - '1'
      val pos = Position(col, row)
      Option.when(pos.isValid)(pos)
    } else None
}