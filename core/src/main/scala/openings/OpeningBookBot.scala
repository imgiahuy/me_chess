package openings

import model._
import scala.util.Random

/** Bot that uses opening book for early game moves */
class OpeningBookBot(fallbackBot: Bot, maxBookMoves: Int = 10) extends Bot {
  
  private val catalog = new InMemoryOpeningCatalog()
  private var bookMovesUsed = 0
  
  override def name: String = s"Opening Book Bot (${fallbackBot.name})"
  override def difficulty: String = fallbackBot.difficulty
  override def description: String = s"Uses opening book for first $maxBookMoves moves, then falls back to ${fallbackBot.name}"
  
  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    // Use opening book for early moves
    if (bookMovesUsed < maxBookMoves && state.moveHistory.nonEmpty) {
      val moveSequence = state.moveHistory.map(moveToUciNotation)
      catalog.findOpening(moveSequence) match {
        case Some(matchResult) =>
          // Get the next move from the opening
          val nextMoveIndex = moveSequence.length
          if (nextMoveIndex < matchResult.opening.moves.length) {
            val uciMove = matchResult.opening.moves(nextMoveIndex)
            // Convert UCI move to internal Move
            uciToInternalMove(uciMove, availableMoves) match {
              case Some(move) =>
                bookMovesUsed += 1
                return move
              case None => // Move not in legal moves, fall through to fallback
            }
          }
        case None => // No opening found, fall through to fallback
      }
    }
    
    // Fallback to the underlying bot
    fallbackBot.selectMove(state, availableMoves)
  }
  
  /** Convert internal Move to UCI notation */
  private def moveToUciNotation(move: Move): String = {
    val from = positionToUci(move.from)
    val to = positionToUci(move.to)
    val promotion = move.specialMove match {
      case Some(Promotion(pieceType)) => pieceTypeToChar(pieceType)
      case _ => ""
    }
    s"$from$to$promotion"
  }
  
  /** Convert Position to UCI notation (e.g., "e2") */
  private def positionToUci(pos: Position): String = {
    val file = ('a' + pos.col).toChar
    val rank = (8 - pos.row).toString
    s"$file$rank"
  }
  
  /** Convert PieceType to promotion character */
  private def pieceTypeToChar(pt: PieceType): Char = pt match {
    case Queen => 'q'
    case Rook => 'r'
    case Bishop => 'b'
    case Knight => 'n'
    case _ => 'q' // Default to queen
  }
  
  /** Convert UCI move to internal Move */
  private def uciToInternalMove(uciMove: String, availableMoves: List[Move]): Option[Move] = {
    if (uciMove.length < 4) return None
    
    val fromCol = uciMove.charAt(0) - 'a'
    val fromRow = 8 - (uciMove.charAt(1) - '0')
    val toCol = uciMove.charAt(2) - 'a'
    val toRow = 8 - (uciMove.charAt(3) - '0')
    
    val from = Position(col = fromCol, row = fromRow)
    val to = Position(col = toCol, row = toRow)
    
    availableMoves.find(m => m.from == from && m.to == to)
  }
  
  /** Reset book move counter (for new games) */
  def reset(): Unit = {
    bookMovesUsed = 0
  }
}

/** Service for creating opening book bots */
object OpeningBookBotService {
  
  /** Create an opening book bot with a fallback */
  def create(fallbackBot: Bot, maxBookMoves: Int = 10): Bot = {
    new OpeningBookBot(fallbackBot, maxBookMoves)
  }
  
  /** Create an opening book bot with random fallback */
  def createWithRandomFallback(maxBookMoves: Int = 10): Bot = {
    new OpeningBookBot(new RandomBot(), maxBookMoves)
  }
  
  /** Create an opening book bot with smarter fallback */
  def createWithSmarterFallback(maxBookMoves: Int = 10): Bot = {
    new OpeningBookBot(new SmarterBot(), maxBookMoves)
  }
}
