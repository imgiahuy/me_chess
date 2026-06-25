package tournament.client.bot

import model.{Move, PositionState, Color, White, Black}
import parser.FenParser
import service.ChessBotService
import formatter.UciFormatter
import scala.util.{Try, Success, Failure}

/** Adapter for computing UCI moves from FEN using the local chess engine.
  * Accepts FEN, side-to-move, assigned color, game id, and bot type/config.
  * Calls the local chess engine or bot implementation.
  * Returns a UCI move such as `e2e4` or `e7e8q`.
  */
class BotMoveAdapter(botType: String = "stockfish") {
  
  private val bot = ChessBotService.createBot(botType)
  
  /** Compute a UCI move from the current FEN and assigned color.
    *
    * @param fen The current FEN string
    * @param turn The side to move ("white" or "black")
    * @param assignedColor The color assigned to this bot ("white" or "black")
    * @param gameId The game identifier (for logging)
    * @return Either an error message or a UCI move string
    */
  def computeMove(
    fen: String,
    turn: String,
    assignedColor: String,
    gameId: String
  ): Either[String, String] = {
    
    // Check if it's the bot's turn
    if (turn != assignedColor) {
      return Left(s"Not bot's turn: bot is $assignedColor but turn is $turn")
    }
    
    // Parse FEN to PositionState
    val positionState = FenParser.parse(fen) match {
      case Right(state) => state
      case Left(error) => return Left(s"Failed to parse FEN: $error")
    }
    
    // Verify the turn matches the FEN
    val fenTurn = if (positionState.turn == White) "white" else "black"
    if (fenTurn != turn) {
      return Left(s"Turn mismatch: FEN indicates $fenTurn but event says $turn")
    }
    
    // Get legal moves
    val legalMoves = service.GameService.getLegalMoves(positionState, positionState.turn)
    if (legalMoves.isEmpty) {
      return Left("No legal moves available")
    }
    
    // Use bot to select a move
    val selectedMove = Try(bot.selectMove(positionState, legalMoves)) match {
      case Success(move) => move
      case Failure(exception) => return Left(s"Bot move selection failed: ${exception.getMessage}")
    }
    
    // Convert move to UCI format
    val uciMove = moveToUci(selectedMove, positionState)
    
    Right(uciMove)
  }
  
  /** Convert an internal Move to UCI format (e.g., "e2e4" or "e7e8q") */
  private def moveToUci(move: Move, state: PositionState): String = {
    val from = uciSquare(move.from)
    val to = uciSquare(move.to)
    
    // Handle promotion
    move.specialMove match {
      case Some(model.Promotion(pieceType)) =>
        val promotionChar = pieceType match {
          case model.Queen => 'q'
          case model.Rook => 'r'
          case model.Bishop => 'b'
          case model.Knight => 'n'
          case _ => 'q' // Default to queen
        }
        s"$from$to$promotionChar"
      case _ =>
        s"$from$to"
    }
  }
  
  /** Convert a Position to UCI square notation (e.g., (4, 0) -> "e1") */
  private def uciSquare(pos: model.Position): String = {
    val file = ('a' + pos.col).toChar
    val rank = 8 - pos.row
    s"$file$rank"
  }
  
  /** Get the bot's name for logging */
  def botName: String = bot.name
  
  /** Get the bot's difficulty for logging */
  def botDifficulty: String = bot.difficulty
}

object BotMoveAdapter {
  /** Create a new bot move adapter with the specified bot type */
  def apply(botType: String = "stockfish"): BotMoveAdapter = {
    new BotMoveAdapter(botType)
  }
  
  /** Create a new bot move adapter with default settings */
  def default: BotMoveAdapter = {
    new BotMoveAdapter("stockfish")
  }
}
