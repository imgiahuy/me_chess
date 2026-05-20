package service

import formatter.{FenFormatter, UciFormatter}
import parser.{FenParser, UciParser}
import parser.FenParser
import model.*

/** Pure game-rule functions. */
object GameService {

  def createGame(): PositionState = {

    val backRankTypes: Seq[PieceType] =
      Seq(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)

    val makeBackRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, row) => backRankTypes.zipWithIndex.map { case (pt, col) =>
        Position(col, row) -> Piece(color, pt)
      }

    val makePawnRank: (Color, Int) => Seq[(Position, Piece)] =
      (color, row) => (0 until 8).map(col => Position(col, row) -> Piece(color, Pawn))

    val newBoard = Board(
      (makeBackRank(White, 0) ++
        makePawnRank(White, 1) ++
        makePawnRank(Black, 6) ++
        makeBackRank(Black, 7)).toMap
    ) // Initializes to standard chess starting position

    val snap = PositionState(
      board = newBoard,
      turn = White,
      moveHistory = List.empty
    )
    snap
  }

  // ── Move application ────────────────────────────────────────────────────────

  /** Validates and applies `move` to `state`.
   *
   * Returns Right(newState) on success, Left(reason) on failure.
   * Uses for-comprehension over Either for clean chained validation.
   */
  def applyMove(snapshot: PositionState, move: Move): Either[String, PositionState] = {
    for {
      _ <- validate(snapshot, move)
      piece <- snapshot.board
        .pieceAt(move.from)
        .toRight(s"No piece at destination square")
      _ <- isLegalMove(snapshot, move, piece)
      newBoard <- applyMoveToBoard(snapshot.board, move, piece)
      nextTurn = if (snapshot.turn == White) Black else White
    } yield snapshot.copy(board = newBoard, turn = nextTurn, moveHistory = snapshot.moveHistory :+ move)
  }

  /** Runs all precondition checks in order, short-circuiting on first failure. */
  def validate(snapshot: PositionState, move: Move): Either[String, Unit] =
    for {
      _ <- Either.cond(
        move.from.isValid && move.to.isValid,
        (),
        s"Square out of board bounds"
      )
      _ <- Either.cond(
        move.from != move.to,
        (),
        "Source and destination are the same square"
      )
      piece <- snapshot.board
        .pieceAt(move.from)
        .toRight(s"No piece at source square")
      _ <- Either.cond(
        piece.color == snapshot.turn,
        (),
        s"It is ${snapshot.turn}'s turn, not ${piece.color}'s"
      )
      _ <- Either.cond(
        snapshot.board.isEmpty(move.to) || snapshot.board.pieceAt(move.to).exists(_.color != piece.color),
        (),
        s"Cannot capture own piece"
      )
    } yield ()

  // ── Move legality (piece-specific rules) ──────────────────────────────────────

  /** Checks if the move is legal according to piece movement rules and doesn't leave king in check. */
  def isLegalMove(snapshot: PositionState, move: Move, piece: Piece): Either[String, Unit] = {
    for {
      _ <- isPieceMoveLegal(snapshot.board, move, piece)
      boardAfterMove <- applyMoveToBoard(snapshot.board, move, piece)
      _ <- Either.cond(
        !isKingInCheck(boardAfterMove, piece.color),
        (),
        "Move leaves your king in check"
      )
    } yield ()
  }

  /** Validates that a move follows the piece's movement rules. */
  def isPieceMoveLegal(board: Board, move: Move, piece: Piece): Either[String, Unit] = {
    piece.pieceType match {
      case Pawn   => isPawnMoveLegal(board, move, piece)
      case Knight => isKnightMoveLegal(board, move)
      case Bishop => isBishopMoveLegal(board, move)
      case Rook   => isRookMoveLegal(board, move)
      case Queen  => isQueenMoveLegal(board, move)
      case King   => isKingMoveLegal(board, move)
    }
  }

  // ── Pawn movement ───────────────────────────────────────────────────────────

  private def isPawnMoveLegal(board: Board, move: Move, piece: Piece): Either[String, Unit] = {
    val direction = if (piece.color == White) 1 else -1
    val startRow = if (piece.color == White) 1 else 6
    val deltaCol = move.to.col - move.from.col
    val deltaRow = move.to.row - move.from.row

    // Forward move (no capture)
    if (deltaCol == 0 && deltaRow == direction && board.isEmpty(move.to)) {
      Right(())
    }
    // Double move from starting position
    else if (
      deltaCol == 0 && deltaRow == 2 * direction && move.from.row == startRow &&
      board.isEmpty(move.to) && board.isEmpty(Position(move.from.col, move.from.row + direction))
    ) {
      Right(())
    }
    // Capture diagonally
    else if (
      deltaCol.abs == 1 && deltaRow == direction &&
      board.pieceAt(move.to).exists(_.color != piece.color)
    ) {
      Right(())
    }
    else {
      Left(s"Illegal pawn move from ${move.from} to ${move.to}")
    }
  }

  // ── Knight movement ──────────────────────────────────────────────────────────

  private def isKnightMoveLegal(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = (move.to.col - move.from.col).abs
    val deltaRow = (move.to.row - move.from.row).abs

    val isLegal = (deltaCol == 2 && deltaRow == 1) || (deltaCol == 1 && deltaRow == 2)
    Either.cond(isLegal, (), s"Illegal knight move from ${move.from} to ${move.to}")
  }

  // ── Bishop movement ──────────────────────────────────────────────────────────

  private def isBishopMoveLegal(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = (move.to.col - move.from.col).abs
    val deltaRow = (move.to.row - move.from.row).abs

    if (deltaCol != deltaRow || deltaCol == 0) {
      Left(s"Illegal bishop move from ${move.from} to ${move.to}")
    } else {
      isPathClear(board, move)
    }
  }

  // ── Rook movement ───────────────────────────────────────────────────────────

  private def isRookMoveLegal(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = move.to.col - move.from.col
    val deltaRow = move.to.row - move.from.row

    if ((deltaCol == 0 && deltaRow != 0) || (deltaCol != 0 && deltaRow == 0)) {
      isPathClear(board, move)
    } else {
      Left(s"Illegal rook move from ${move.from} to ${move.to}")
    }
  }

  // ── Queen movement ──────────────────────────────────────────────────────────

  private def isQueenMoveLegal(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = move.to.col - move.from.col
    val deltaRow = move.to.row - move.from.row

    val isDiagonal = deltaCol.abs == deltaRow.abs && deltaCol != 0
    val isStraight = (deltaCol == 0 && deltaRow != 0) || (deltaCol != 0 && deltaRow == 0)

    if (isDiagonal || isStraight) {
      isPathClear(board, move)
    } else {
      Left(s"Illegal queen move from ${move.from} to ${move.to}")
    }
  }

  // ── King movement ───────────────────────────────────────────────────────────

  private def isKingMoveLegal(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = (move.to.col - move.from.col).abs
    val deltaRow = (move.to.row - move.from.row).abs

    Either.cond(
      deltaCol <= 1 && deltaRow <= 1 && (deltaCol != 0 || deltaRow != 0),
      (),
      s"Illegal king move from ${move.from} to ${move.to}"
    )
  }

  // ── Path checking ───────────────────────────────────────────────────────────

  /** Checks if the path between two squares is clear (for sliding pieces). */
  private def isPathClear(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = move.to.col - move.from.col
    val deltaRow = move.to.row - move.from.row

    val stepCol = if (deltaCol > 0) 1 else if (deltaCol < 0) -1 else 0
    val stepRow = if (deltaRow > 0) 1 else if (deltaRow < 0) -1 else 0

    var current = Position(move.from.col + stepCol, move.from.row + stepRow)

    while (current != move.to) {
      if (!board.isEmpty(current)) {
        return Left(s"Path blocked from ${move.from} to ${move.to}")
      }
      current = Position(current.col + stepCol, current.row + stepRow)
    }

    Right(())
  }

  // ── Board updates ───────────────────────────────────────────────────────────

  /** Applies a move to the board, handling captures and piece removal. */
  private def applyMoveToBoard(board: Board, move: Move, piece: Piece): Either[String, Board] = {
    val boardWithoutSource = board.squares - move.from
    val boardWithMove = boardWithoutSource + (move.to -> piece)
    Right(Board(boardWithMove))
  }

  // ── Check detection ─────────────────────────────────────────────────────────

  /** Returns true if the given color's king is in check. */
  def isKingInCheck(board: Board, color: Color): Boolean = {
    val kingPos = board.allPieces.find { case (_, p) => p.color == color && p.pieceType == King }
    kingPos.exists { case (pos, _) => isSquareAttackedBy(board, pos, color.opposite) }
  }

  /** Returns true if the given square is attacked by the opponent color. */
  private def isSquareAttackedBy(board: Board, targetPos: Position, attackerColor: Color): Boolean = {
    board.piecesOf(attackerColor).exists { case (pos, piece) =>
      isPieceMoveLegal(board, Move(pos, targetPos), piece).isRight
    }
  }

  /** The game is over when at least one king has been captured. */
  def isGameOver(snapshot: PositionState): Boolean =
    snapshot.board.kingsAlive.size < 2

  /** Returns the winner if exactly one king remains; None if still ongoing
   * or if both kings were lost simultaneously (theoretical edge case).
   */
  def winner(snapshot: PositionState): Option[Color] =
    snapshot.board.kingsAlive.toList match {
      case color :: Nil => Some(color)
      case _            => None
    }

  /** Returns true if the current player is in checkmate. */
  def isCheckmate(snapshot: PositionState): Boolean = {
    isKingInCheck(snapshot.board, snapshot.turn) && !hasLegalMoves(snapshot)
  }

  /** Returns true if the current player is in stalemate (not in check but no legal moves). */
  def isStalemate(snapshot: PositionState): Boolean = {
    !isKingInCheck(snapshot.board, snapshot.turn) && !hasLegalMoves(snapshot)
  }

  /** Returns true if the current player has at least one legal move available. */
  def hasLegalMoves(snapshot: PositionState): Boolean = {
    snapshot.board.piecesOf(snapshot.turn).exists { case (pos, piece) =>
      (0 until 8).exists { toCol =>
        (0 until 8).exists { toRow =>
          val move = Move(pos, Position(toCol, toRow))
          applyMove(snapshot, move).isRight
        }
      }
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /** True when the active player still has at least one piece on the board. */
  def currentPlayerHasPieces(snapshot: PositionState): Boolean =
    snapshot.board.piecesOf(snapshot.turn).nonEmpty

  def save(snapshot: PositionState): String = {
    val fen = FenFormatter.fenFormatter(snapshot)
    val moves = UciFormatter.uciListFormatter(snapshot.moveHistory)
    s"$fen\nMOVES:\n$moves"
  }

  def load(input: String): PositionState =
    val lines = input.linesIterator.toList
    if lines.isEmpty then throw new Exception("Empty game file")

    val moves: List[Move] =
      if lines.length >= 2 && lines(1) == "MOVES:" then

        val movesStr =
          lines.dropWhile(_ != "MOVES:").drop(1).mkString("\n")

        UciParser.parseMoveList(movesStr) match
          case Right(ms) => ms
          case Left(err) =>
            throw new Exception(err)

      else List.empty

    FenParser.parse(lines.head) match
      case Right(state) =>
        PositionState(state.board, state.turn, moves )

      case Left(err) =>
        throw new Exception(err)
}