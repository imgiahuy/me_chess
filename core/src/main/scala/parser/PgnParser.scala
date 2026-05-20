package parser

import model.{PositionState, Move, Position, White, Black, Board, Piece, PieceType}
import service.GameService

/** Parses PGN (Portable Game Notation) format files.
 *
 * Supports:
 * - PGN tag pairs (metadata)
 * - Standard Algebraic Notation (SAN) moves
 * - Move numbers and comments (ignored)
 * - Game results
 */
object PgnParser {

  /** Represents parsed PGN data. */
  case class PgnGame(
    tags: Map[String, String],
    moves: List[Move]
  )

  /** Parses a complete PGN string (with tags and moves).
   *
   * @param pgnText The PGN text to parse
   * @return Either error message or parsed game
   */
  def parse(pgnText: String): Either[String, PgnGame] = {
    val lines = pgnText.linesIterator.toList
    val (tagLines, moveLines) = lines.span(_.trim.startsWith("["))

    val tagsResult = parseTags(tagLines)
    val movesResult = parseMoves(moveLines.mkString(" "))

    for {
      tags <- tagsResult
      moves <- movesResult
    } yield PgnGame(tags, moves)
  }

  /** Parses PGN tag pairs (metadata).
   *
   * Format: [TagName "TagValue"]
   */
  private def parseTags(lines: List[String]): Either[String, Map[String, String]] = {
    val tagPattern = """^\s*\[(\w+)\s+"([^"]*)"\s*\]\s*$""".r
    val tags = scala.collection.mutable.Map[String, String]()

    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.nonEmpty) {
        tagPattern.findFirstMatchIn(trimmed) match {
          case Some(m) =>
            val name = m.group(1)
            val value = m.group(2)
            tags(name) = value
          case None =>
            // Ignore malformed tags
        }
      }
    }

    Right(tags.toMap)
  }

  /** Parses move list in PGN format.
   *
   * Handles:
   * - Move numbers (1., 2., etc.)
   * - Standard Algebraic Notation (SAN)
   * - Comments in braces (ignored)
   * - Results (1-0, 0-1, 1/2-1/2, *)
   */
  private def parseMoves(moveText: String): Either[String, List[Move]] = {
    // Remove comments (text in braces)
    val noComments = moveText.replaceAll("\\{[^}]*\\}", "")

    // Remove result indicators
    val noResults = noComments.replaceAll("(1-0|0-1|1/2-1/2|\\*)", "")

    // Split into tokens
    val tokens = noResults.split("\\s+").toList.filter(_.nonEmpty)

    // Filter out move numbers and parse moves
    val moveTokens = tokens.filter(!_.matches("\\d+\\."))

    // Reconstruct game and convert SAN to UCI
    val gameState = GameService.createGame()
    convertSanToMoves(gameState, moveTokens)
  }

  /** Converts SAN notation moves to internal Move objects.
   *
   * Reconstructs the game state to properly interpret ambiguous moves.
   */
  private def convertSanToMoves(
    initialState: PositionState,
    sanMoves: List[String]
  ): Either[String, List[Move]] = {
    var currentState = initialState
    val moves = scala.collection.mutable.ListBuffer[Move]()

    for (sanMove <- sanMoves) {
      sanToMove(currentState, sanMove) match {
        case Right(move) =>
          GameService.applyMove(currentState, move) match {
            case Right(newState) =>
              moves += move
              currentState = newState
            case Left(err) =>
              return Left(s"Invalid move '$sanMove': $err")
          }
        case Left(err) =>
          return Left(s"Failed to parse move '$sanMove': $err")
      }
    }

    Right(moves.toList)
  }

  /** Converts a single SAN move to a Move object.
   *
   * Handles:
   * - Pawn moves: e4, exd5, e8=Q
   * - Piece moves: Nf3, Bxc5, Rab1
   * - Castling: O-O, O-O-O
   * - Check/mate notation: +, #
   */
  private def sanToMove(state: PositionState, san: String): Either[String, Move] = {
    val cleanSan = san.replaceAll("[+#]", "").trim

    // Castling
    if (cleanSan == "O-O") {
      return findCastlingMove(state, kingside = true)
    }
    if (cleanSan == "O-O-O") {
      return findCastlingMove(state, kingside = false)
    }

    // Parse piece type and destination
    val pieceLetter = if (cleanSan.head.isUpper) cleanSan.head else ' '
    val pieceType = pieceLetter match {
      case 'K' => Some(model.King)
      case 'Q' => Some(model.Queen)
      case 'R' => Some(model.Rook)
      case 'B' => Some(model.Bishop)
      case 'N' => Some(model.Knight)
      case _   => None // Pawn move
    }

    val moveStr = if (pieceType.isDefined) cleanSan.drop(1) else cleanSan

    // Extract destination square and promotion
    val (destStr, promotion) = if (moveStr.contains("=")) {
      val parts = moveStr.split("=")
      (parts(0), Some(parts(1).head))
    } else {
      (moveStr, None)
    }

    // Remove capture indicator
    val destWithoutCapture = destStr.replaceAll("x", "")

    // Extract destination square (last 2 chars)
    if (destWithoutCapture.length < 2) {
      return Left(s"Invalid destination in move: $san")
    }

    val destSquareStr = destWithoutCapture.takeRight(2)
    val destSquare = algebraicToPosition(destSquareStr)

    // Extract disambiguation (if any)
    val disambiguation = destWithoutCapture.dropRight(2)

    // Find the moving piece
    findMovingPiece(state, pieceType, destSquare, disambiguation) match {
      case Some(fromPos) => Right(Move(fromPos, destSquare))
      case None          => Left(s"No piece can make move: $san")
    }
  }

  /** Finds the source square for a move given destination and piece type. */
  private def findMovingPiece(
    state: PositionState,
    pieceType: Option[model.PieceType],
    destSquare: Position,
    disambiguation: String
  ): Option[Position] = {
    val candidates = if (pieceType.isDefined) {
      // Non-pawn move
      state.board.piecesOf(state.turn).filter { case (_, p) =>
        p.pieceType == pieceType.get
      }
    } else {
      // Pawn move
      state.board.piecesOf(state.turn).filter { case (_, p) =>
        p.pieceType == model.Pawn
      }
    }

    // Filter by disambiguation
    val filtered = if (disambiguation.nonEmpty) {
      candidates.filter { case (pos, _) =>
        if (disambiguation.length == 1) {
          val c = disambiguation.head
          if (c.isLetter) {
            // File disambiguation
            pos.col == (c - 'a')
          } else {
            // Rank disambiguation
            pos.row == (c.asDigit - 1)
          }
        } else {
          false
        }
      }
    } else {
      candidates
    }

    // Find a piece that can legally move to destination
    filtered.find { case (pos, piece) =>
      val move = Move(pos, destSquare)
      GameService.isPieceMoveLegal(state.board, move, piece).isRight
    }.map(_._1)
  }

  /** Finds a castling move. */
  private def findCastlingMove(state: PositionState, kingside: Boolean): Either[String, Move] = {
    val kingRow = if (state.turn == White) 0 else 7
    val kingPos = Position(4, kingRow)
    val destCol = if (kingside) 6 else 2

    state.board.pieceAt(kingPos) match {
      case Some(piece) if piece.pieceType == model.King =>
        Right(Move(kingPos, Position(destCol, kingRow)))
      case _ =>
        Left("Invalid castling move")
    }
  }

  /** Converts algebraic notation to Position.
   *
   * @param algebraic Two-character string (e.g., "e4", "a1")
   */
  private def algebraicToPosition(algebraic: String): Position = {
    if (algebraic.length != 2) {
      throw new IllegalArgumentException(s"Invalid algebraic notation: $algebraic")
    }
    val col = algebraic.head - 'a'
    val row = algebraic.last.asDigit - 1
    Position(col, row)
  }

  /** Reconstructs a game from PGN, returning the final game state. */
  def parseToGameState(pgnText: String): Either[String, PositionState] = {
    parse(pgnText).map { pgnGame =>
      var state = GameService.createGame()
      pgnGame.moves.foreach { move =>
        GameService.applyMove(state, move) match {
          case Right(newState) => state = newState
          case Left(_)         => // Skip invalid moves
        }
      }
      state
    }
  }
}
