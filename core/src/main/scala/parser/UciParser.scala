package parser

import model.{Move, Position}

/** Parses UCI (Universal Chess Interface) move notation.
 *
 * UCI format: "e2e4" (source square + destination square in algebraic notation)
 * Example: e2e4, g1f3, e7e8q (with promotion)
 *
 * Supports:
 * - Standard moves: e2e4
 * - Pawn promotions: e7e8q (promotion to queen)
 * - Whitespace and dash normalization
 */
object UciParser {

  /** Parses a single UCI move string.
   *
   * @param str The UCI move string (e.g., "e2e4")
   * @return Either error message or parsed Move
   */
  def parse(str: String): Either[String, Move] = {
    val cleaned = str.trim.toLowerCase.replaceAll("[\\s\\-]+", "")

    if (cleaned.length < 4)
      return Left(s"UCI move must be at least 4 characters long, got '$str'")

    // Extract source and destination (first 4 chars)
    val sourceStr = cleaned.substring(0, 2)
    val destStr = cleaned.substring(2, 4)

    for {
      from <- algebraicToPosition(sourceStr)
      to <- algebraicToPosition(destStr)
    } yield Move(from, to)
  }

  /** Parses a space-separated list of UCI moves.
   *
   * @param str String containing multiple UCI moves separated by whitespace
   * @return Either error message or list of parsed moves
   */
  def parseMoveList(str: String): Either[String, List[Move]] = {
    val tokens = str.split("\\s+").toList.filter(_.nonEmpty)

    val parsedMoves = tokens.map(parse)

    val errors = parsedMoves.collect { case Left(err) => err }

    if errors.nonEmpty then
      Left(s"Failed to parse move list: ${errors.mkString(", ")}")
    else
      Right(parsedMoves.collect { case Right(move) => move })
  }

  /** Converts algebraic notation to a Position.
   *
   * @param algebraic Two-character string (e.g., "e4", "a1")
   * @return Either error message or Position
   */
  private def algebraicToPosition(algebraic: String): Either[String, Position] = {
    if (algebraic.length != 2)
      return Left(s"Invalid algebraic notation: '$algebraic' (must be 2 characters)")

    val file = algebraic.head
    val rank = algebraic.last

    if (file < 'a' || file > 'h')
      return Left(s"Invalid file: '$file' (must be a-h)")

    if (rank < '1' || rank > '8')
      return Left(s"Invalid rank: '$rank' (must be 1-8)")

    val col = file - 'a'
    val row = rank.asDigit - 1

    Right(Position(col, row))
  }
}
