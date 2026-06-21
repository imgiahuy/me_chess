package model

/** Base trait for chess bots */
trait Bot {
  def name: String
  def difficulty: String
  def description: String

  /** Select the best move for the given position
    *
    * @param state Current game position
    * @param availableMoves All legal moves available in this position
    * @return The selected move
    */
  def selectMove(state: PositionState, availableMoves: List[Move]): Move
}

/** Simple random bot that selects a random legal move */
class RandomBot extends Bot {
  override def name: String = "Random Bot"
  override def difficulty: String = "Easy"
  override def description: String = "Makes completely random moves. Perfect for beginners to practice against."

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")
    availableMoves(scala.util.Random.nextInt(availableMoves.length))
  }
}

/** Bot that prefers capturing pieces and center control */
class CapturePreferenceBot extends Bot {
  override def name: String = "Capture Bot"
  override def difficulty: String = "Easy"
  override def description: String = "Loves to capture pieces when possible. Will also try to control the center squares."

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")

    // Score moves: 100 for capture, 10 for center control, 1 base
    val scoredMoves = availableMoves.map { move =>
      val score =
        (if (state.board.pieceAt(move.to).exists(_.color != state.turn)) 100 else 0) +
        (if (isCenter(move.to)) 10 else 0) +
        1
      (move, score)
    }

    scoredMoves.maxBy(_._2)._1
  }

  private def isCenter(pos: Position): Boolean = {
    pos.col >= 2 && pos.col <= 5 && pos.row >= 2 && pos.row <= 5
  }
}

/** Bot that prioritizes material value - takes the most valuable pieces */
class GreedyBot extends Bot {
  override def name: String = "Greedy Bot"
  override def difficulty: String = "Medium"
  override def description: String = "Greedy for material! Always captures the most valuable piece available. Good at tactics."

  private val pieceValues: Map[PieceType, Int] = Map(
    Pawn -> 100,
    Knight -> 320,
    Bishop -> 330,
    Rook -> 500,
    Queen -> 900,
    King -> 20000
  )

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")

    val scoredMoves = availableMoves.map { move =>
      var score = 0

      // Capturing opponent pieces (value-based)
      state.board.pieceAt(move.to).foreach { capturedPiece =>
        if (capturedPiece.color != state.turn) {
          score += pieceValues.getOrElse(capturedPiece.pieceType, 0)
        }
      }

      // Promotion bonus
      move.specialMove match {
        case Some(Promotion(pieceType)) =>
          score += pieceValues.getOrElse(pieceType, 0)
        case _ =>
      }

      // Slight preference for center control
      if (isCentral(move.to)) score += 10

      // Avoid moving to squares attacked by opponent pawns (unless capturing)
      if (score < 100 && isAttackedByPawn(state.board, move.to, state.turn.opposite)) {
        score -= 50
      }

      (move, score)
    }

    scoredMoves.maxBy(_._2)._1
  }

  private def isCentral(pos: Position): Boolean = {
    (pos.col >= 2 && pos.col <= 5 && pos.row >= 2 && pos.row <= 5)
  }

  private def isAttackedByPawn(board: Board, pos: Position, attackerColor: Color): Boolean = {
    val pawnDirection = if (attackerColor == White) 1 else -1
    val pawnRow = pos.row - pawnDirection

    if (pawnRow < 0 || pawnRow > 7) return false

    List(pos.col - 1, pos.col + 1).exists { col =>
      if (col >= 0 && col <= 7) {
        board.pieceAt(Position(col, pawnRow)).exists(p =>
          p.pieceType == Pawn && p.color == attackerColor
        )
      } else false
    }
  }
}

/** Defensive bot that prioritizes king safety and solid positions */
class DefensiveBot extends Bot {
  override def name: String = "Defensive Bot"
  override def difficulty: String = "Medium"
  override def description: String = "Plays safe and solid. Prioritizes king safety, avoids unnecessary risks, and maintains a strong defensive structure."

  private val pieceValues: Map[PieceType, Int] = Map(
    Pawn -> 100,
    Knight -> 320,
    Bishop -> 330,
    Rook -> 500,
    Queen -> 900,
    King -> 20000
  )

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")

    val myColor = state.turn
    val kingPos = findKing(state.board, myColor)

    val scoredMoves = availableMoves.map { move =>
      var score = 0

      // King safety: prefer moves that don't expose the king
      if (move.from == kingPos) {
        // Prefer castling for king safety
        move.specialMove match {
          case Some(CastlingKingSide) | Some(CastlingQueenSide) =>
            score += 500
          case _ =>
            // Discourage random king moves
            score -= 100
        }
      }

      // Capturing: still consider captures but with less weight
      state.board.pieceAt(move.to).foreach { capturedPiece =>
        if (capturedPiece.color != myColor) {
          val captureValue = pieceValues.getOrElse(capturedPiece.pieceType, 0)
          val attackerValue = state.board.pieceAt(move.from).map(p => pieceValues.getOrElse(p.pieceType, 0)).getOrElse(0)
          // Only capture if it's a good trade or free piece
          if (captureValue >= attackerValue || !isSquareAttacked(state.board, move.to, myColor.opposite)) {
            score += captureValue
          }
        }
      }

      // Avoid moving to attacked squares (defensive)
      if (isSquareAttacked(state.board, move.to, myColor.opposite)) {
        val pieceValue = state.board.pieceAt(move.from).map(p => pieceValues.getOrElse(p.pieceType, 0)).getOrElse(0)
        score -= pieceValue / 2  // Penalty proportional to piece value
      }

      // Prefer pawn moves that strengthen structure (early ranks for white, late for black)
      state.board.pieceAt(move.from).foreach { piece =>
        if (piece.pieceType == Pawn) {
          val isWhite = myColor == White
          val advanceBonus = if (isWhite) move.to.row else (7 - move.to.row)
          score += advanceBonus * 5

          // Bonus for pawns protecting each other
          if (hasPawnNeighbor(state.board, move.to, myColor)) {
            score += 20
          }
        }
      }

      // Slight preference for staying back (defensive)
      val isWhite = myColor == White
      val rowDistanceFromStart = if (isWhite) move.to.row else (7 - move.to.row)
      score += rowDistanceFromStart * 2

      (move, score)
    }

    scoredMoves.maxBy(_._2)._1
  }

  private def findKing(board: Board, color: Color): Position = {
    board.allPieces.find { case (_, p) => p.pieceType == King && p.color == color }.map(_._1)
      .getOrElse(Position(4, if (color == White) 0 else 7))
  }

  private def isSquareAttacked(board: Board, pos: Position, attackerColor: Color): Boolean = {
    board.piecesOf(attackerColor).exists { case (fromPos, piece) =>
      // Simplified attack check - just check if piece can legally move there
      val deltaCol = (pos.col - fromPos.col).abs
      val deltaRow = (pos.row - fromPos.row).abs

      piece.pieceType match {
        case Knight => (deltaCol == 2 && deltaRow == 1) || (deltaCol == 1 && deltaRow == 2)
        case King => deltaCol <= 1 && deltaRow <= 1
        case _ => false  // Simplified - don't check sliding pieces thoroughly
      }
    }
  }

  private def hasPawnNeighbor(board: Board, pos: Position, color: Color): Boolean = {
    List(-1, 1).exists { colOffset =>
      val neighborPos = Position(pos.col + colOffset, pos.row)
      board.pieceAt(neighborPos).exists(p => p.pieceType == Pawn && p.color == color)
    }
  }
}

/** Aggressive bot that loves to attack and check the enemy king */
class AggressiveBot extends Bot {
  override def name: String = "Aggressive Bot"
  override def difficulty: String = "Medium"
  override def description: String = "Attacks relentlessly! Loves to give check, push forward, and create threats. Exciting to play against!"

  private val pieceValues: Map[PieceType, Int] = Map(
    Pawn -> 100,
    Knight -> 320,
    Bishop -> 330,
    Rook -> 500,
    Queen -> 900,
    King -> 20000
  )

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")

    val myColor = state.turn
    val enemyKingPos = findKing(state.board, myColor.opposite)

    val scoredMoves = availableMoves.map { move =>
      var score = 0

      // CHECK! Highest priority
      if (wouldGiveCheck(state, move)) {
        score += 1000
      }

      // Capturing pieces
      state.board.pieceAt(move.to).foreach { capturedPiece =>
        if (capturedPiece.color != myColor) {
          score += pieceValues.getOrElse(capturedPiece.pieceType, 0)
        }
      }

      // Aggressive: moving toward enemy king
      val distanceToEnemyKing = math.abs(move.to.col - enemyKingPos.col) + math.abs(move.to.row - enemyKingPos.row)
      score += (14 - distanceToEnemyKing) * 15  // Closer to enemy king = higher score

      // Prefer forward moves (aggressive advancement)
      val isWhite = myColor == White
      val forwardBonus = if (isWhite) move.to.row else (7 - move.to.row)
      score += forwardBonus * 10

      // Bonus for attacking squares near enemy king
      if (distanceToEnemyKing <= 2) {
        score += 50
      }

      // Promotion is very aggressive
      move.specialMove match {
        case Some(Promotion(_)) => score += 800
        case _ =>
      }

      (move, score)
    }

    scoredMoves.maxBy(_._2)._1
  }

  private def findKing(board: Board, color: Color): Position = {
    board.allPieces.find { case (_, p) => p.pieceType == King && p.color == color }.map(_._1)
      .getOrElse(Position(4, if (color == White) 0 else 7))
  }

  private def wouldGiveCheck(state: PositionState, move: Move): Boolean = {
    // Simulate the move and check if enemy king is in check
    val piece = state.board.pieceAt(move.from).getOrElse(return false)
    val newBoard = state.board.squares - move.from + (move.to -> piece)
    val enemyColor = state.turn.opposite
    val enemyKingPos = newBoard.find { case (_, p) => p.pieceType == King && p.color == enemyColor }.map(_._1)

    enemyKingPos.exists { kingPos =>
      // Check if any of our pieces can attack the enemy king
      newBoard.filter(_._2.color == state.turn).exists { case (fromPos, p) =>
        val deltaCol = (kingPos.col - fromPos.col).abs
        val deltaRow = (kingPos.row - fromPos.row).abs
        p.pieceType match {
          case Knight => (deltaCol == 2 && deltaRow == 1) || (deltaCol == 1 && deltaRow == 2)
          case King => deltaCol <= 1 && deltaRow <= 1
          case _ => false
        }
      }
    }
  }
}

/** Smarter bot using piece-square tables for positional evaluation */
class SmarterBot extends Bot {
  override def name: String = "Smarter Bot"
  override def difficulty: String = "Hard"
  override def description: String = "Uses advanced positional evaluation with piece-square tables. Understands piece placement and basic strategy."

  private val pieceValues: Map[PieceType, Int] = Map(
    Pawn -> 100,
    Knight -> 320,
    Bishop -> 330,
    Rook -> 500,
    Queen -> 900,
    King -> 20000
  )

  // Piece-square tables for positional evaluation (simplified - only for pawns and knights)
  // Values encourage pieces to go to good squares
  private val pawnTable: Array[Array[Int]] = Array(
    Array(0,  0,  0,  0,  0,  0,  0,  0),
    Array(50, 50, 50, 50, 50, 50, 50, 50),
    Array(10, 10, 20, 30, 30, 20, 10, 10),
    Array(5,  5, 10, 25, 25, 10,  5,  5),
    Array(0,  0,  0, 20, 20,  0,  0,  0),
    Array(5, -5,-10,  0,  0,-10, -5,  5),
    Array(5, 10, 10,-20,-20, 10, 10,  5),
    Array(0,  0,  0,  0,  0,  0,  0,  0)
  )

  private val knightTable: Array[Array[Int]] = Array(
    Array(-50,-40,-30,-30,-30,-30,-40,-50),
    Array(-40,-20,  0,  0,  0,  0,-20,-40),
    Array(-30,  0, 10, 15, 15, 10,  0,-30),
    Array(-30,  5, 15, 20, 20, 15,  5,-30),
    Array(-30,  0, 15, 20, 20, 15,  0,-30),
    Array(-30,  5, 10, 15, 15, 10,  5,-30),
    Array(-40,-20,  0,  5,  5,  0,-20,-40),
    Array(-50,-40,-30,-30,-30,-30,-40,-50)
  )

  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")

    val myColor = state.turn
    val isWhite = myColor == White

    val scoredMoves = availableMoves.map { move =>
      var score = 0

      // Material value of capture
      state.board.pieceAt(move.to).foreach { capturedPiece =>
        if (capturedPiece.color != myColor) {
          score += pieceValues.getOrElse(capturedPiece.pieceType, 0)
        }
      }

      // Position evaluation using piece-square tables
      state.board.pieceAt(move.from).foreach { piece =>
        val row = if (isWhite) 7 - move.to.row else move.to.row
        val col = move.to.col

        val positionBonus = piece.pieceType match {
          case Pawn => pawnTable(row)(col)
          case Knight => knightTable(row)(col)
          case _ => 0
        }
        score += positionBonus
      }

      // Mobility bonus (prefer moves that give more options)
      score += 10

      // Promotion bonus
      move.specialMove match {
        case Some(Promotion(Queen)) => score += 900
        case Some(Promotion(Rook)) => score += 500
        case Some(Promotion(_)) => score += 300
        case _ =>
      }

      // Castling bonus (king safety)
      move.specialMove match {
        case Some(CastlingKingSide) | Some(CastlingQueenSide) => score += 60
        case _ =>
      }

      // Slight randomness to avoid repetitive play
      score += scala.util.Random.nextInt(10)

      (move, score)
    }

    scoredMoves.maxBy(_._2)._1
  }
}

/** Bot metadata for UI display */
case class BotInfo(id: String, name: String, difficulty: String, description: String)

/** Factory for creating bots */
object BotFactory {
  def createBot(botType: String): Bot = botType.toLowerCase match {
    case "random" => new RandomBot()
    case "capture" => new CapturePreferenceBot()
    case "greedy" => new GreedyBot()
    case "defensive" => new DefensiveBot()
    case "aggressive" => new AggressiveBot()
    case "smarter" => new SmarterBot()
    case "stockfish" | "uci" => new RandomBot() // UCI bots require external engine setup via UciBotService
    case "stockfish-easy" => new RandomBot()
    case "stockfish-medium" => new RandomBot()
    case _ => new RandomBot()
  }

  def availableBots: List[String] = List("random", "capture", "greedy", "defensive", "aggressive", "smarter")

  def availableBotInfo: List[BotInfo] = List(
    BotInfo("random", "Random Bot", "Easy", "Makes completely random moves. Perfect for beginners to practice against."),
    BotInfo("capture", "Capture Bot", "Easy", "Loves to capture pieces when possible. Will also try to control the center squares."),
    BotInfo("greedy", "Greedy Bot", "Medium", "Greedy for material! Always captures the most valuable piece available. Good at tactics."),
    BotInfo("defensive", "Defensive Bot", "Medium", "Plays safe and solid. Prioritizes king safety, avoids unnecessary risks, and maintains a strong defensive structure."),
    BotInfo("aggressive", "Aggressive Bot", "Medium", "Attacks relentlessly! Loves to give check, push forward, and create threats. Exciting to play against!"),
    BotInfo("smarter", "Smarter Bot", "Hard", "Uses advanced positional evaluation with piece-square tables. Understands piece placement and basic strategy.")
  )
}

