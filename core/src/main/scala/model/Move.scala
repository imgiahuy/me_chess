package model

/** An immutable description of one player's move: a source and destination square.
 *
 * This is a plain data carrier with no validation — validation belongs in
 * the logic layer (GameEngine).
 */
case class Move(from: Position, to: Position) {

  /** Long-algebraic string, e.g. "e2e4". */
  def toAlgebraic: String = s"${from.toAlgebraic}${to.toAlgebraic}"

  def fromAlgebraic(str: String): Option[Move] =
    if (str.length != 4) None
    else for {
      from <- Position.fromAlgebraic(str.take(2))
      to <- Position.fromAlgebraic(str.drop(2))
    } yield Move(from, to)
}

object Move {

  def fromAlgebraic(str: String): Option[Move] =
    if (str.length != 4) None
    else for {
      from <- Position.fromAlgebraic(str.take(2))
      to   <- Position.fromAlgebraic(str.drop(2))
    } yield Move(from, to)
}