package parser

import model.{Black, Board, Piece, PieceType, Player, Position, PositionState, White}

/** Enhanced FEN (Forsyth-Edwards Notation) parser with better error handling.
 *
 * FEN format: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
 * Fields:
 * 1. Piece placement (from white's perspective)
 * 2. Active color (w or b)
 * 3. Castling availability (KQkq or -)
 * 4. En passant target square (- or square like e3)
 * 5. Halfmove clock (for 50-move rule)
 * 6. Fullmove number
 *
 * Currently supports fields 1-2; fields 3-6 are parsed but not enforced.
 */
object FenParser:

  /** Parses a FEN string and returns a PositionState.
   *
   * @param str FEN notation string
   * @return Either error message or parsed PositionState
   */
  def parse(str: String): Either[String, PositionState] =
    val parts = str.trim.split(" ")

    if parts.length < 2 then
      return Left("Invalid FEN: not enough fields (minimum 2 required)")

    val boardPart = parts(0)
    val turnPart  = parts(1)

    for {
      squares <- parseBoardPart(boardPart)
      turn <- parseTurnPart(turnPart)
    } yield PositionState(
      board = Board(squares),
      turn = turn,
      moveHistory = List.empty,
      whitePlayer = Player("White"),
      blackPlayer = Player("Black"),
      timeControl = None
    )

  /** Parses the board placement part of FEN.
   *
   * Format: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
   * Numbers represent empty squares, letters represent pieces.
   *
   * @param boardStr The board part of FEN
   * @return Either error message or map of positions to pieces
   */
  private def parseBoardPart(boardStr: String): Either[String, Map[Position, Piece]] =
    val rows = boardStr.split("/")

    if rows.length != 8 then
      return Left(s"Invalid FEN: board must have 8 rows, got ${rows.length}")

    val result = scala.collection.mutable.Map[Position, Piece]()

    for ((rowStr, rowIdx) <- rows.zipWithIndex) {
      val rowNum = 7 - rowIdx
      var col = 0

      for (c <- rowStr) {
        if c.isDigit then
          val emptyCount = c.asDigit
          if col + emptyCount > 8 then
            return Left(s"Invalid FEN: row $rowNum has more than 8 squares")
          col += emptyCount
        else if c.isLetter then
          if col >= 8 then
            return Left(s"Invalid FEN: row $rowNum has more than 8 squares")

          val color = if c.isUpper then White else Black
          try {
            val pieceType = PieceType.fromAbbrev(c.toUpper)
            result(Position(col, rowNum)) = Piece(color, pieceType)
            col += 1
          } catch {
            case _: IllegalArgumentException =>
              return Left(s"Invalid FEN: unknown piece '$c' at row $rowNum, column $col")
          }
        else
          return Left(s"Invalid FEN: unexpected character '$c' in board part at row $rowNum")
      }

      if col != 8 then
        return Left(s"Invalid FEN: row $rowNum has $col squares, expected 8")
    }

    Right(result.toMap)

  /** Parses the active color part of FEN.
   *
   * @param turnStr "w" for white or "b" for black
   * @return Either error message or Color
   */
  private def parseTurnPart(turnStr: String): Either[String, model.Color] =
    turnStr.trim match
      case "w" => Right(White)
      case "b" => Right(Black)
      case _   => Left(s"Invalid FEN: turn must be 'w' or 'b', got '$turnStr'")
