package service

import formatter.{FenFormatter, UciFormatter, PgnFormatter}
import parser.{FenParser, UciParser}
import parser.FenParser
import model.*

/** Pure game-rule functions. */
object GameService {

  def createGame(whitePlayerName: String, blackPlayerName: String, timeControl: Option[TimeControl] = None): PositionState = {

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

    val (whiteTime, blackTime) = timeControl match {
      case Some(tc) =>
        (Some(PlayerTime(tc.initialTimeMs)), Some(PlayerTime(tc.initialTimeMs)))
      case None => (None, None)
    }

    val snap = PositionState(
      board = newBoard,
      turn = White,
      moveHistory = List.empty,
      whitePlayer = Player(whitePlayerName),
      blackPlayer = Player(blackPlayerName),
      timeControl = timeControl,
      whiteTime = whiteTime,
      blackTime = blackTime,
      positionHistory = List(newBoard)
    )
    snap
  }

  // Backward compatibility method
  def createGame(): PositionState = {
    createGame("White", "Black", None)
  }

  // ── Move application ────────────────────────────────────────────────────────

  /** Validates and applies `move` to `state`.
    *
    * Returns Right(newState) on success, Left(reason) on failure.
    * Uses for-comprehension over Either for clean chained validation.
    */
  def applyMove(snapshot: PositionState, move: Move): Either[String, PositionState] = {
    // Check if game is already over
    if (snapshot.gameResult != Ongoing) {
      return Left(s"Game is already over: ${snapshot.gameResult}")
    }

    // Normalize castling moves: UciParser always emits row 0 for 0-0/0-0-0 shorthand.
    // If the move is a castling special move but the from-square row doesn't match the
    // current player's king row, remap it to the correct row.
    val normalizedMove = move.specialMove match {
      case Some(CastlingKingSide) | Some(CastlingQueenSide) =>
        val kingRow = if (snapshot.turn == White) 0 else 7
        if (move.from.row != kingRow) {
          val isKingSide = move.specialMove == Some(CastlingKingSide)
          Move(
            Position(4, kingRow),
            Position(if (isKingSide) 6 else 2, kingRow),
            move.specialMove
          )
        } else move
      case _ => move
    }

    for {
      _ <- validate(snapshot, normalizedMove)
      piece <- snapshot.board
        .pieceAt(normalizedMove.from)
        .toRight(s"No piece at source square")
      _ <- isLegalMove(snapshot, normalizedMove, piece)
      newBoard <- applyMoveToBoard(snapshot.board, normalizedMove, piece, snapshot)
      nextTurn = if (snapshot.turn == White) Black else White
      // Track halfmoves for fifty-move rule
      isEnPassantCapture = piece.pieceType == Pawn && 
        (normalizedMove.to.col - normalizedMove.from.col).abs == 1 &&
        snapshot.board.isEmpty(normalizedMove.to) &&
        snapshot.moveHistory.nonEmpty
      isCaptureOrPawn = piece.pieceType == Pawn ||
                        snapshot.board.pieceAt(normalizedMove.to).isDefined ||
                        isEnPassantCapture
      halfmovesSinceLastCaptureOrPawn = if (isCaptureOrPawn) 0 else snapshot.halfmovesSinceLastCaptureOrPawn + 1
      // Add to position history for threefold repetition
      newPositionHistory = snapshot.positionHistory :+ newBoard
      // Update player times
      (newWhiteTime, newBlackTime) = updateTime(snapshot)
    } yield {
      val newState = snapshot.copy(
        board = newBoard,
        turn = nextTurn,
        moveHistory = snapshot.moveHistory :+ normalizedMove,
        halfmovesSinceLastCaptureOrPawn = halfmovesSinceLastCaptureOrPawn,
        positionHistory = newPositionHistory,
        whiteTime = newWhiteTime,
        blackTime = newBlackTime
      )
      // Update game result based on draw or checkmate conditions
      updateGameResult(newState)
    }
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
      _ <- isPieceMoveLegal(snapshot.board, move, piece, snapshot)
      boardAfterMove <- applyMoveToBoard(snapshot.board, move, piece, snapshot)
      _ <- Either.cond(
        !isKingInCheck(boardAfterMove, piece.color),
        (),
        "Move leaves your king in check"
      )
    } yield ()
  }

  /** Validates that a move follows the piece's movement rules. */
  def isPieceMoveLegal(board: Board, move: Move, piece: Piece, snapshot: PositionState = null): Either[String, Unit] = {
    piece.pieceType match {
      case Pawn   => isPawnMoveLegal(board, move, piece, if (snapshot != null) snapshot else PositionState(board, White, List(), Player(""), Player(""), timeControl = None))
      case Knight => isKnightMoveLegal(board, move)
      case Bishop => isBishopMoveLegal(board, move)
      case Rook   => isRookMoveLegal(board, move)
      case Queen  => isQueenMoveLegal(board, move)
      case King   => isKingMoveLegal(board, move, snapshot)
    }
  }

  /** Simplified version for attack detection - doesn't check castling to avoid infinite recursion */
  private def isPieceMoveLegalForAttack(board: Board, move: Move, piece: Piece): Either[String, Unit] = {
    piece.pieceType match {
      case Pawn   => isPawnMoveLegal(board, move, piece, PositionState(board, White, List(), Player(""), Player(""), timeControl = None))
      case Knight => isKnightMoveLegal(board, move)
      case Bishop => isBishopMoveLegal(board, move)
      case Rook   => isRookMoveLegal(board, move)
      case Queen  => isQueenMoveLegal(board, move)
      case King   => isKingMoveLegalForAttack(board, move)
    }
  }

  /** Simplified king move check for attack detection - only checks regular moves, not castling */
  private def isKingMoveLegalForAttack(board: Board, move: Move): Either[String, Unit] = {
    val deltaCol = (move.to.col - move.from.col).abs
    val deltaRow = (move.to.row - move.from.row).abs

    if (deltaCol <= 1 && deltaRow <= 1 && (deltaCol != 0 || deltaRow != 0)) {
      Right(())
    } else {
      Left(s"Illegal king move from ${move.from} to ${move.to}")
    }
  }

  // ── Pawn movement ───────────────────────────────────────────────────────────

   private def isPawnMoveLegal(board: Board, move: Move, piece: Piece, snapshot: PositionState): Either[String, Unit] = {
    val direction = if (piece.color == White) 1 else -1
    val startRow = if (piece.color == White) 1 else 6
    val promotionRow = if (piece.color == White) 7 else 0
    val deltaCol = move.to.col - move.from.col
    val deltaRow = move.to.row - move.from.row

    // Forward move (no capture)
    if (deltaCol == 0 && deltaRow == direction && board.isEmpty(move.to)) {
      validatePawnPromotion(move, piece.color)
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
      validatePawnPromotion(move, piece.color)
    }
    // En passant (auto-detected - no specialMove flag required)
    else if (deltaCol.abs == 1 && deltaRow == direction && board.isEmpty(move.to)) {
      // Check if en passant is actually possible
      val capturedPawnPos = Position(move.to.col, move.from.row)
      val isValidEnPassant = for {
        lastMove <- snapshot.moveHistory.lastOption
        capturedPawn <- board.pieceAt(capturedPawnPos)
        if capturedPawn.color == piece.color.opposite && capturedPawn.pieceType == Pawn
        if lastMove.from.col == capturedPawnPos.col
        if (lastMove.from.row - lastMove.to.row).abs == 2  // Opponent moved pawn 2 squares
        if lastMove.to == capturedPawnPos
      } yield ()

      isValidEnPassant.toRight(s"Invalid en passant move")
    }
    else {
      Left(s"Illegal pawn move from ${move.from} to ${move.to}")
    }
  }

  private def validatePawnPromotion(move: Move, pieceColor: Color): Either[String, Unit] = {
    val promotionRow = if (pieceColor == White) 7 else 0
    if (move.to.row == promotionRow) {
      move.specialMove match {
        case Some(Promotion(pt)) =>
          if (Set(Queen, Rook, Bishop, Knight).contains(pt)) Right(())
          else Left(s"Invalid promotion piece type: $pt")
        case None => Left(s"Pawn promotion required at row ${promotionRow}")
        case _ => Left(s"Invalid move for pawn promotion")
      }
    } else {
      move.specialMove match {
        case Some(Promotion(_)) => Left(s"Pawn promotion not allowed at row ${move.to.row}")
        case _ => Right(())
      }
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

  // ── King movement ───────────────────────────────────────────────────────

  private def isKingMoveLegal(board: Board, move: Move, snapshot: PositionState = null): Either[String, Unit] = {
    val deltaCol = (move.to.col - move.from.col).abs
    val deltaRow = (move.to.row - move.from.row).abs

    // Regular king move
    if (deltaCol <= 1 && deltaRow <= 1 && (deltaCol != 0 || deltaRow != 0)) {
      Right(())
    }
    // Castling
    else if (deltaRow == 0 && deltaCol == 2 && move.specialMove.isDefined) {
      if (snapshot == null) {
        Left("Cannot validate castling without game snapshot")
      } else {
        isCastlingLegal(board, move, snapshot)
      }
    }
    else {
      Left(s"Illegal king move from ${move.from} to ${move.to}")
    }
  }

  private def isCastlingLegal(board: Board, move: Move, snapshot: PositionState): Either[String, Unit] = {
    val kingColor = board.pieceAt(move.from).map(_.color).getOrElse(return Left("No king at source"))
    val isKingSide = move.to.col > move.from.col
    val rookCol = if (isKingSide) 7 else 0
    val expectedCastlingMove = if (isKingSide) CastlingKingSide else CastlingQueenSide

    // Verify castling move type is specified correctly
    if (move.specialMove != Some(expectedCastlingMove)) {
      return Left(s"Incorrect castling type specified")
    }

    // Check if king is in check
    if (isKingInCheck(board, kingColor)) {
      return Left("Cannot castle while in check")
    }

    // Check if king or rook has moved before by checking starting squares
    val kingStartPos = Position(4, move.from.row)
    val rookStartPos = Position(rookCol, move.from.row)

    val kingHasMoved = snapshot.moveHistory.exists { m =>
      m.from == kingStartPos || m.to == kingStartPos
    }

    val rookHasMoved = snapshot.moveHistory.exists { m =>
      m.from == rookStartPos || m.to == rookStartPos
    }

    if (kingHasMoved || rookHasMoved) {
      return Left("King or rook has already moved")
    }

    // Check if rook is at the corner
    val rookAtCorner = board.pieceAt(Position(rookCol, move.from.row))
      .filter(_.pieceType == Rook)
      .isDefined
    if (!rookAtCorner) return Left("No rook at corner")

    // Check if path is clear
    val pathClear = for {
      col <- (Math.min(move.from.col, rookCol) + 1) to (Math.max(move.from.col, rookCol) - 1)
      if !board.isEmpty(Position(col, move.from.row))
    } yield false

    if (pathClear.nonEmpty) {
      return Left("Path is not clear for castling")
    }

    // Check if king passes through check
    val kingPath = for {
      col <- Math.min(move.from.col, move.to.col) to Math.max(move.from.col, move.to.col)
    } yield Position(col, move.from.row)

    val tempBoard = Board(board.squares - move.from)
    for (pos <- kingPath) {
      if (isSquareAttackedBy(tempBoard, pos, kingColor.opposite)) {
        return Left("King would pass through check")
      }
    }

    Right(())
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

  // ── Board updates ───────────────────────────────────────────────────────

  /** Applies a move to the board, handling captures and piece removal. */
  private def applyMoveToBoard(board: Board, move: Move, piece: Piece, snapshot: PositionState = null): Either[String, Board] = {
    move.specialMove match {
      // Handle pawn promotion
      case Some(Promotion(promotionPiece)) =>
        val boardWithoutSource = board.squares - move.from
        val promotedPiece = Piece(piece.color, promotionPiece)
        val boardWithMove = boardWithoutSource + (move.to -> promotedPiece)
        Right(Board(boardWithMove))

      // Handle en passant (explicitly marked)
      case Some(EnPassant) =>
        val boardWithoutSource = board.squares - move.from
        val capturedPawnPos = Position(move.to.col, move.from.row)
        val boardWithCapture = boardWithoutSource - capturedPawnPos
        val boardWithMove = boardWithCapture + (move.to -> piece)
        Right(Board(boardWithMove))

      // Handle castling
      case Some(CastlingKingSide) | Some(CastlingQueenSide) =>
        val isKingSide = move.specialMove == Some(CastlingKingSide)
        val rookCol = if (isKingSide) 7 else 0
        val newRookCol = if (isKingSide) 5 else 3

        val boardWithoutPieces = board.squares - move.from - Position(rookCol, move.from.row)
        val king = Piece(piece.color, King)
        val rook = board.pieceAt(Position(rookCol, move.from.row)) match {
          case Some(r) => r
          case None => return Left("No rook at corner for castling")
        }
        val boardWithNewPositions = boardWithoutPieces +
          (move.to -> king) +
          (Position(newRookCol, move.from.row) -> rook)
        Right(Board(boardWithNewPositions))

      // Regular move (with possible capture) - auto-detect en passant
      case None =>
        // Check if this is an en passant capture (diagonal pawn move to empty square)
        val isEnPassant = piece.pieceType == Pawn && 
          (move.to.col - move.from.col).abs == 1 &&
          board.isEmpty(move.to) &&
          snapshot != null &&
          snapshot.moveHistory.nonEmpty

        if (isEnPassant) {
          val capturedPawnPos = Position(move.to.col, move.from.row)
          val boardWithoutSource = board.squares - move.from
          val boardWithCapture = boardWithoutSource - capturedPawnPos
          val boardWithMove = boardWithCapture + (move.to -> piece)
          Right(Board(boardWithMove))
        } else {
          val boardWithoutSource = board.squares - move.from
          val boardWithMove = boardWithoutSource + (move.to -> piece)
          Right(Board(boardWithMove))
        }

      case _ => Left(s"Unknown special move type")
    }
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
      isPieceMoveLegalForAttack(board, Move(pos, targetPos), piece).isRight
    }
  }

  /** Updates the game result based on current board state */
  private def updateGameResult(snapshot: PositionState): PositionState = {
    // Check for resignation first
    if (snapshot.hasWhiteResigned) {
      return snapshot.copy(gameResult = Resignation(Black))
    }
    if (snapshot.hasBlackResigned) {
      return snapshot.copy(gameResult = Resignation(White))
    }

    // Check for checkmate
    if (isCheckmate(snapshot)) {
      val winner = snapshot.turn.opposite
      return snapshot.copy(gameResult = Checkmate(winner))
    }

    // Check for stalemate
    if (isStalemate(snapshot)) {
      return snapshot.copy(gameResult = Draw(Stalemate))
    }

    // Check for sufficient material
    if (!hasSufficientMaterial(snapshot.board)) {
      return snapshot.copy(gameResult = Draw(InsufficientMaterial))
    }

    // Check for fifty-move rule
    if (snapshot.halfmovesSinceLastCaptureOrPawn >= 100) {
      return snapshot.copy(gameResult = Draw(FiftyMoveRule))
    }

    // Check for threefold repetition
    if (countBoardRepetitions(snapshot) >= 3) {
      return snapshot.copy(gameResult = Draw(ThreefoldRepetition))
    }

    snapshot
  }

  /** Check for threefold repetition */
  private def countBoardRepetitions(snapshot: PositionState): Int = {
    snapshot.positionHistory.lastOption match {
      case Some(lastBoard) =>
        snapshot.positionHistory.count(board => boardsAreEqual(board, lastBoard))
      case None => 1
    }
  }

  private def boardsAreEqual(board1: Board, board2: Board): Boolean = {
    board1.squares == board2.squares
  }

  /** Check if board has sufficient material for checkmate */
  private def hasSufficientMaterial(board: Board): Boolean = {
    val whitePieces = board.piecesOf(White).map(_._2.pieceType).toList
    val blackPieces = board.piecesOf(Black).map(_._2.pieceType).toList

    // Both sides have only king - insufficient
    if (whitePieces == List(King) && blackPieces == List(King)) return false

    // One has only king, other has only king and one minor piece (knight or bishop) - insufficient
    val whiteNonKing = whitePieces.filter(_ != King)
    val blackNonKing = blackPieces.filter(_ != King)

    if (whiteNonKing.isEmpty && blackNonKing.nonEmpty) {
      val blackMinors = blackNonKing.filter(pt => pt == Knight || pt == Bishop)
      if (blackMinors.length == 1) return false
    }

    if (blackNonKing.isEmpty && whiteNonKing.nonEmpty) {
      val whiteMinors = whiteNonKing.filter(pt => pt == Knight || pt == Bishop)
      if (whiteMinors.length == 1) return false
    }

    // King vs King + Bishop(s) of same color - insufficient
    val whiteBishops = whiteNonKing.count(_ == Bishop)
    val blackBishops = blackNonKing.count(_ == Bishop)

    if (whiteNonKing.forall(_ == Bishop) && blackNonKing.isEmpty && whiteBishops > 0) {
      // Check if all bishops are on same color squares
      val bishopSquares = board.piecesOf(White).filter(_._2.pieceType == Bishop).map(_._1)
      val allSameColor = bishopSquares.map(pos => (pos.col + pos.row) % 2).toSet.size == 1
      if (allSameColor) return false
    }

    if (blackNonKing.forall(_ == Bishop) && whiteNonKing.isEmpty && blackBishops > 0) {
      val bishopSquares = board.piecesOf(Black).filter(_._2.pieceType == Bishop).map(_._1)
      val allSameColor = bishopSquares.map(pos => (pos.col + pos.row) % 2).toSet.size == 1
      if (allSameColor) return false
    }

    true
  }

  /** The game is over when at least one king has been captured or game has ended by other means. */
  def isGameOver(snapshot: PositionState): Boolean =
    snapshot.board.kingsAlive.size < 2 || snapshot.gameResult != Ongoing

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
    val promotionRow = if (snapshot.turn == White) 7 else 0
    val hasRegularMove = snapshot.board.piecesOf(snapshot.turn).exists { case (pos, piece) =>
      (0 until 8).exists { toCol =>
        (0 until 8).exists { toRow =>
          val toPos = Position(toCol, toRow)
          if (piece.pieceType == Pawn && toRow == promotionRow) {
            applyMoveWithoutGameResultCheck(snapshot, Move(pos, toPos, Some(Promotion(Queen)))).isRight
          } else {
            applyMoveWithoutGameResultCheck(snapshot, Move(pos, toPos)).isRight
          }
        }
      }
    }
    if (hasRegularMove) return true
    // Also check castling moves
    val kingRow = if (snapshot.turn == White) 0 else 7
    val castlingMoves = List(
      Move(Position(4, kingRow), Position(6, kingRow), Some(CastlingKingSide)),
      Move(Position(4, kingRow), Position(2, kingRow), Some(CastlingQueenSide))
    )
    castlingMoves.exists(m => applyMoveWithoutGameResultCheck(snapshot, m).isRight)
  }

  /** Simplified version of applyMove that doesn't check game result - used to avoid infinite recursion in hasLegalMoves */
  private def applyMoveWithoutGameResultCheck(snapshot: PositionState, move: Move): Either[String, PositionState] = {
    val normalizedMove = move.specialMove match {
      case Some(CastlingKingSide) | Some(CastlingQueenSide) =>
        val kingRow = if (snapshot.turn == White) 0 else 7
        if (move.from.row != kingRow) {
          val isKingSide = move.specialMove == Some(CastlingKingSide)
          Move(Position(4, kingRow), Position(if (isKingSide) 6 else 2, kingRow), move.specialMove)
        } else move
      case _ => move
    }
    for {
      _ <- validate(snapshot, normalizedMove)
      piece <- snapshot.board
        .pieceAt(normalizedMove.from)
        .toRight(s"No piece at source square")
      _ <- isLegalMove(snapshot, normalizedMove, piece)
      newBoard <- applyMoveToBoard(snapshot.board, normalizedMove, piece, snapshot)
      nextTurn = if (snapshot.turn == White) Black else White
      isEnPassantCapture = piece.pieceType == Pawn && 
        (normalizedMove.to.col - normalizedMove.from.col).abs == 1 &&
        snapshot.board.isEmpty(normalizedMove.to) &&
        snapshot.moveHistory.nonEmpty
      isCaptureOrPawn = piece.pieceType == Pawn ||
                        snapshot.board.pieceAt(normalizedMove.to).isDefined ||
                        isEnPassantCapture
      halfmovesSinceLastCaptureOrPawn = if (isCaptureOrPawn) 0 else snapshot.halfmovesSinceLastCaptureOrPawn + 1
      newPositionHistory = snapshot.positionHistory :+ newBoard
      // Update player times
      (newWhiteTime, newBlackTime) = updateTime(snapshot)
    } yield {
      snapshot.copy(
        board = newBoard,
        turn = nextTurn,
        moveHistory = snapshot.moveHistory :+ normalizedMove,
        halfmovesSinceLastCaptureOrPawn = halfmovesSinceLastCaptureOrPawn,
        positionHistory = newPositionHistory,
        whiteTime = newWhiteTime,
        blackTime = newBlackTime
      )
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /** Update player times after a move */
  private def updateTime(snapshot: PositionState): (Option[PlayerTime], Option[PlayerTime]) = {
    snapshot.timeControl match {
      case Some(tc) =>
        val currentTime = System.currentTimeMillis()
        if (snapshot.turn == White) {
          // Deduct time used by white (the player who just moved)
          val updatedWhiteTime = snapshot.whiteTime.map { wt =>
            val timeUsed = currentTime - wt.lastUpdatedAt
            val newRemaining = (wt.remainingTimeMs - timeUsed + tc.incrementMs).max(0)
            PlayerTime(newRemaining, currentTime)
          }
          // Reset black's lastUpdatedAt so their clock starts from now
          val resetBlackTime = snapshot.blackTime.map(bt => bt.copy(lastUpdatedAt = currentTime))
          (updatedWhiteTime, resetBlackTime)
        } else {
          // Deduct time used by black (the player who just moved)
          val updatedBlackTime = snapshot.blackTime.map { bt =>
            val timeUsed = currentTime - bt.lastUpdatedAt
            val newRemaining = (bt.remainingTimeMs - timeUsed + tc.incrementMs).max(0)
            PlayerTime(newRemaining, currentTime)
          }
          // Reset white's lastUpdatedAt so their clock starts from now
          val resetWhiteTime = snapshot.whiteTime.map(wt => wt.copy(lastUpdatedAt = currentTime))
          (resetWhiteTime, updatedBlackTime)
        }
      case None => (snapshot.whiteTime, snapshot.blackTime)
    }
  }

  /** True when the active player still has at least one piece on the board. */
  def currentPlayerHasPieces(snapshot: PositionState): Boolean =
    snapshot.board.piecesOf(snapshot.turn).nonEmpty

  def exportToPgn(snapshot: PositionState, event: String, site: String): String = {
    val tags = PgnFormatter.PgnTags(
      event = event,
      site = site,
      date = snapshot.creationDate.toString,
      white = snapshot.whitePlayer.name,
      black = snapshot.blackPlayer.name
    )
    PgnFormatter.toPgn(snapshot, tags)
  }

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
        PositionState(
          board = state.board,
          turn = state.turn,
          moveHistory = moves,
          whitePlayer = Player("White"),
          blackPlayer = Player("Black"),
          timeControl = None
        )

      case Left(err) =>
        throw new Exception(err)

  // ── Resignation and Time Control ────────────────────────────────────────

  /** Player resigns from the game */
  def resign(snapshot: PositionState, color: Color): PositionState = {
    if (snapshot.gameResult != Ongoing) {
      snapshot
    } else {
      val updated = color match {
        case White => snapshot.copy(hasWhiteResigned = true)
        case Black => snapshot.copy(hasBlackResigned = true)
      }
      updateGameResult(updated)
    }
  }

  /** Offer draw by mutual agreement */
  def offerDraw(snapshot: PositionState): PositionState = {
    if (snapshot.gameResult != Ongoing) {
      snapshot
    } else {
      snapshot.copy(gameResult = Draw(MutualAgreement))
    }
  }

  /** Check if a player's time has expired */
  def isTimeExpired(snapshot: PositionState, color: Color): Boolean = {
    color match {
      case White => snapshot.whiteTime.exists(_.isTimeOver)
      case Black => snapshot.blackTime.exists(_.isTimeOver)
    }
  }

  /** Get remaining time for a player in milliseconds */
  def getRemainingTime(snapshot: PositionState, color: Color): Option[Long] = {
    color match {
      case White => snapshot.whiteTime.map(_.getCurrentTime)
      case Black => snapshot.blackTime.map(_.getCurrentTime)
    }
  }

  /** Update remaining time for both players (typically called after each move) */
  def updateTimeAfterMove(snapshot: PositionState, timeSpentMs: Long, incrementMs: Long = 0): PositionState = {
    val updatedTime = snapshot.turn match {
      case White =>
        snapshot.whiteTime.map { t =>
          val used = t.useTime(timeSpentMs)
          used.addTime(Math.max(0, incrementMs))
        }
      case Black =>
        snapshot.blackTime.map { t =>
          val used = t.useTime(timeSpentMs)
          used.addTime(Math.max(0, incrementMs))
        }
    }

    val updated = snapshot.turn match {
      case White => snapshot.copy(whiteTime = updatedTime)
      case Black => snapshot.copy(blackTime = updatedTime)
    }

    // Check if the current player (who just moved) ran out of time during their turn
    snapshot.turn match {
      case White if updated.whiteTime.exists(_.isTimeOver) =>
        updated.copy(gameResult = TimeOut(Black))
      case Black if updated.blackTime.exists(_.isTimeOver) =>
        updated.copy(gameResult = TimeOut(White))
      case _ => updated
    }
  }

  // ── Legal Moves Calculation ──────────────────────────────────────────────

  /** Get all legal moves for a color in a position */
  def getLegalMoves(snapshot: PositionState, color: Color): List[Move] = {
    val legalMoves = scala.collection.mutable.ListBuffer[Move]()
    val promotionRow = if (color == White) 7 else 0
    val promotionPieces = List(Queen, Rook, Bishop, Knight)

    snapshot.board.piecesOf(color).foreach { case (pos, piece) =>
      (0 until 8).foreach { toCol =>
        (0 until 8).foreach { toRow =>
          val toPos = Position(toCol, toRow)
          if (piece.pieceType == Pawn && toRow == promotionRow) {
            promotionPieces.foreach { pt =>
              val move = Move(pos, toPos, Some(Promotion(pt)))
              if (applyMove(snapshot, move).isRight) {
                legalMoves += move
              }
            }
          } else {
            val move = Move(pos, toPos)
            if (applyMove(snapshot, move).isRight) {
              legalMoves += move
            }
          }
        }
      }
    }

    // Also include castling moves
    val kingRow = if (color == White) 0 else 7
    List(
      Move(Position(4, kingRow), Position(6, kingRow), Some(CastlingKingSide)),
      Move(Position(4, kingRow), Position(2, kingRow), Some(CastlingQueenSide))
    ).foreach { m =>
      if (applyMove(snapshot, m).isRight) {
        legalMoves += m
      }
    }

    legalMoves.toList
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────
}
