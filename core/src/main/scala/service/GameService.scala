package service

import model._
import parser.FEN

/** Pure game-rule functions. */
object GameService {

  def createGame(): Snapshot = {

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

    val snap = Snapshot(
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
  def applyMove(snapshot: Snapshot, move: Move): Either[String, Snapshot] = {
    for {
      _ <- validate(snapshot, move)
      piece <- snapshot.board
        .pieceAt(move.from)
        .toRight(s"No piece at ${move.from.toAlgebraic}")
      newBoard = Board(
        snapshot.board.squares - move.from + (move.to -> piece)
      ) // Naive move application; does not handle captures, promotions, castling, en passant, etc.
      nextTurn = if (snapshot.turn == White) Black else White
    } yield snapshot.copy(board = newBoard, turn = nextTurn, moveHistory = snapshot.moveHistory :+ move)
  }

  /** Runs all precondition checks in order, short-circuiting on first failure. */
  def validate(snapshot: Snapshot, move: Move): Either[String, Unit] =
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
        .toRight(s"No piece at ${move.from.toAlgebraic}")
      _ <- Either.cond(
        piece.color == snapshot.turn,
        (),
        s"It is ${snapshot.turn}'s turn, not ${piece.color}'s"
      )
    } yield ()

  // ── Game-over detection ──────────────────────────────────────────────────────

  /** The game is over when at least one king has been captured. */
  def isGameOver(snapshot: Snapshot): Boolean =
    snapshot.board.kingsAlive.size < 2

  /** Returns the winner if exactly one king remains; None if still ongoing
   * or if both kings were lost simultaneously (theoretical edge case).
   */
  def winner(snapshot: Snapshot): Option[Color] =
    snapshot.board.kingsAlive.toList match {
      case color :: Nil => Some(color)
      case _            => None
    }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /** True when the active player still has at least one piece on the board. */
  def currentPlayerHasPieces(snapshot: Snapshot): Boolean =
    snapshot.board.piecesOf(snapshot.turn).nonEmpty

  def save(snapshot: Snapshot): String = {
    val fen = FEN.toFEN(snapshot.board, snapshot.turn)
    val moves = snapshot.moveHistory.map(_.toAlgebraic).mkString("\n")
    s"$fen\nMOVES:\n$moves"
  }

  def load(input: String): Snapshot = {
    val lines = input.linesIterator.toList
    if (lines.isEmpty) throw new Exception("Empty game file")

    val (board, turn) = FEN.fromFEN(lines.head)

    val moves =
      if (lines.length >= 2 && lines(1) == "MOVES:") {
        lines.drop(2).map(Move.fromAlgebraic).collect { case Some(m) => m }
      } else List.empty

    Snapshot(board, turn, moves)
  }
}