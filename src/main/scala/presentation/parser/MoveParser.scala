package presentation.parser

import chess.domain.{Move, Position}

/** Parses text input into `Move` values.
 *
 * Accepted formats (all case-insensitive):
 *   "e2e4"   – compact
 *   "e2 e4"  – space-separated
 *   "e2-e4"  – hyphen-separated
 *
 * Returns None for anything that cannot be interpreted as a legal square pair.
 * This is a pure function — no state, no side-effects.
 */
object MoveParser {

  private val separatorPattern = "[\\s\\-]+"

  /** Strips separators, then delegates to position parsing. */
  def parse(input: String): Option[Move] = {
    val cleaned = input.trim.toLowerCase.replaceAll(separatorPattern, "")
    Option.when(cleaned.length == 4) {
      for {
        from <- Position.fromAlgebraic(cleaned.take(2))
        to   <- Position.fromAlgebraic(cleaned.drop(2))
      } yield Move(from, to)
    }.flatten
  }

  /** Parses a single algebraic square string ("a1"–"h8"). Delegates to Position. */
  def parsePosition(s: String): Option[Position] = Position.fromAlgebraic(s)
}