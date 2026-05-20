package formatter

import model.{PositionState, Move, Position, White, Black, Pawn, King, Queen, Rook, Bishop, Knight, Piece}
import service.GameService

/** Formats a chess game to PGN (Portable Game Notation) format. */
object PgnFormatter {

  /** Represents PGN metadata tags. */
  case class PgnTags(
    event: String = "?",
    site: String = "?",
    date: String = "????.??.??",
    round: String = "?",
    white: String = "?",
    black: String = "?",
    result: String = "*"
  )

  /** Converts a game state to PGN format with metadata.
   *
   * @param state The final game state
   * @param tags Optional PGN metadata tags
   * @return PGN string representation
   */
  def toPgn(state: PositionState, tags: PgnTags = PgnTags()): String = {
    val tagSection = formatTags(tags)
    val moveSection = formatMoves(state.moveHistory, state)
    s"$tagSection\n\n$moveSection"
  }

  /** Formats PGN tags section. */
  private def formatTags(tags: PgnTags): String = {
    List(
      s"""[Event "${tags.event}"]""",
      s"""[Site "${tags.site}"]""",
      s"""[Date "${tags.date}"]""",
      s"""[Round "${tags.round}"]""",
      s"""[White "${tags.white}"]""",
      s"""[Black "${tags.black}"]""",
      s"""[Result "${tags.result}"]"""
    ).mkString("\n")
  }

  /** Formats moves in PGN notation (algebraic notation with move numbers).
   *
   * Reconstructs the game from the move history and converts each move
   * to standard algebraic notation (SAN).
   */
  private def formatMoves(moves: List[Move], finalState: PositionState): String = {
    if (moves.isEmpty) return ""

    var currentState = GameService.createGame()
    val pgnMoves = scala.collection.mutable.ListBuffer[String]()

    moves.zipWithIndex.foreach { case (move, idx) =>
      val moveNum = idx / 2 + 1
      val isWhiteMove = idx % 2 == 0

      val sanMove = moveToSan(currentState, move)
      val moveStr = if (isWhiteMove) s"$moveNum. $sanMove" else sanMove

      pgnMoves += moveStr

      // Apply move to current state
      GameService.applyMove(currentState, move) match {
        case Right(newState) => currentState = newState
        case Left(_)         => // Skip invalid moves
      }
    }

    // Format moves in 80-character lines
    formatMoveLines(pgnMoves.toList, finalState)
  }

  /** Converts a move to Standard Algebraic Notation (SAN).
   *
   * Examples: e4, Nf3, Bxc5, O-O (castling), e8=Q (promotion)
   */
  private def moveToSan(state: PositionState, move: Move): String = {
    val piece = state.board.pieceAt(move.from).get
    val isCapture = state.board.pieceAt(move.to).isDefined
    val captureStr = if (isCapture) "x" else ""

    piece.pieceType match {
      case Pawn =>
        val destSquare = squareToAlgebraic(move.to)
        if (isCapture) {
          val file = ('a' + move.from.col).toChar
          s"$file$captureStr$destSquare"
        } else {
          destSquare
        }

      case King =>
        // Check for castling
        if (move.from.col == 4 && move.from.row == (if (piece.color == White) 0 else 7)) {
          if (move.to.col == 6) "O-O"      // Kingside castling
          else if (move.to.col == 2) "O-O-O" // Queenside castling
          else s"K$captureStr${squareToAlgebraic(move.to)}"
        } else {
          s"K$captureStr${squareToAlgebraic(move.to)}"
        }

      case _ =>
        val piece_letter = piece.pieceType match {
          case Queen  => "Q"
          case Rook   => "R"
          case Bishop => "B"
          case Knight => "N"
          case _      => ""
        }

        // Check for disambiguation (multiple pieces of same type can move to same square)
        val disambiguation = getDisambiguation(state, move, piece)

        s"$piece_letter$disambiguation$captureStr${squareToAlgebraic(move.to)}"
    }
  }

  /** Determines if disambiguation is needed (e.g., Nbd2 vs Nfd2). */
  private def getDisambiguation(state: PositionState, move: Move, piece: Piece): String = {
    val otherPieces = state.board.piecesOf(piece.color).filter { case (pos, p) =>
      pos != move.from && p.pieceType == piece.pieceType
    }

    val canMoveToSameSquare = otherPieces.exists { case (pos, p) =>
      val testMove = Move(pos, move.to)
      GameService.isPieceMoveLegal(state.board, testMove, p).isRight
    }

    if (!canMoveToSameSquare) {
      ""
    } else {
      // Disambiguate by file first, then by rank
      val sameFile = otherPieces.exists { case (pos, _) => pos.col == move.from.col }
      if (!sameFile) {
        ('a' + move.from.col).toChar.toString
      } else {
        (move.from.row + 1).toString
      }
    }
  }

  /** Converts a Position to algebraic notation (e.g., e4, a1). */
  private def squareToAlgebraic(pos: Position): String = {
    s"${('a' + pos.col).toChar}${pos.row + 1}"
  }

  /** Formats moves into lines with proper spacing (max 80 chars per line). */
  private def formatMoveLines(moves: List[String], finalState: PositionState): String = {
    val lines = scala.collection.mutable.ListBuffer[String]()
    var currentLine = ""

    moves.foreach { move =>
      val testLine = if (currentLine.isEmpty) move else s"$currentLine $move"
      if (testLine.length > 80) {
        lines += currentLine
        currentLine = move
      } else {
        currentLine = testLine
      }
    }

    if (currentLine.nonEmpty) {
      lines += currentLine
    }

    // Add result at the end
    val result = finalState match {
      case _ if GameService.isCheckmate(finalState) =>
        if (finalState.turn == White) "0-1" else "1-0"
      case _ if GameService.isStalemate(finalState) => "1/2-1/2"
      case _ => "*"
    }

    val allLines = lines.toList :+ result
    allLines.mkString("\n")
  }
}
