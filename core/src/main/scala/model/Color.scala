package model

/** Represents the two player colours.
 *
 * Sealed so the compiler exhaustively checks pattern matches.
 * `opposite` is a pure function with no side-effects.
 */
sealed trait Color {
  def opposite: Color
  override def toString: String = this match {
    case White => "White"
    case Black => "Black"
  }
}

case object White extends Color { val opposite: Color = Black }
case object Black extends Color { val opposite: Color = White }