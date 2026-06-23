package formatter

import model.{Black, PieceType, PositionState, White, Position}

/** Formats a PositionState to FEN (Forsyth-Edwards Notation) string.
 *
 * FEN format: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
 * 
 * This formatter generates the board position and active color fields.
 * Castling rights, en passant, halfmove clock, and fullmove number are set to defaults.
 */
object FenFormatter {
  
  /** Converts a PositionState to FEN notation.
   *
   * @param positionState The game position to format
   * @return FEN string representation
   */
  def fenFormatter(positionState: PositionState): String = {
    val boardStr = formatBoard(positionState)
    val turnStr = formatTurn(positionState.turn)
    s"$boardStr $turnStr - - 0 1"
  }

  /** Formats the board part of FEN (piece placement).
   *
   * Iterates from rank 8 to rank 1, encoding pieces and empty squares.
   * Numbers represent consecutive empty squares, letters represent pieces.
   */
  private def formatBoard(positionState: PositionState): String = {
    (7 to 0 by -1).map { row =>
      formatRank(positionState, row)
    }.mkString("/")
  }

  /** Formats a single rank (row) in FEN notation.
   *
   * @param positionState The game position
   * @param row The rank number (0-7)
   * @return FEN string for this rank
   */
  private def formatRank(positionState: PositionState, row: Int): String = {
    var empty = 0
    val rankStr = new StringBuilder()

    for (col <- 0 to 7) {
      positionState.board.pieceAt(Position(col, row)) match {
        case Some(piece) =>
          // Flush empty square count
          if (empty > 0) {
            rankStr.append(empty)
            empty = 0
          }
          // Add piece symbol
          val symbol = piece.symbol
          rankStr.append(symbol)

        case None =>
          empty += 1
      }
    }

    // Flush remaining empty squares
    if (empty > 0) {
      rankStr.append(empty)
    }

    rankStr.toString
  }

  /** Formats the active color part of FEN.
   *
   * @param turn The color whose turn it is
   * @return "w" for white or "b" for black
   */
  private def formatTurn(turn: model.Color): String = {
    turn match {
      case White => "w"
      case Black => "b"
    }
  }
}
