package service

import model.{PositionState, Bot, BotFactory, Move}
import engine.{UciBotService, UciEngine, EngineManager}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._

/** Service for handling bot moves and interactions */
object BotService {
  
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val uciBotService = UciBotService()
  
  // Initialize UCI bot service on load
  uciBotService.initialize()

  /** Get the next move for a bot in the given position
    *
    * @param bot The bot to query
    * @param state Current game state
    * @return The bot's chosen move
    */
  def getBotMove(bot: Bot, state: PositionState): Either[String, Move] = {
    try {
      val legalMoves = GameService.getLegalMoves(state, state.turn)
      if (legalMoves.isEmpty) {
        Left("No legal moves available")
      } else {
        Right(bot.selectMove(state, legalMoves))
      }
    } catch {
      case e: Exception => Left(s"Bot error: ${e.getMessage}")
    }
  }

  /** Play a move from a bot and return the new state
    *
    * @param bot The bot to query
    * @param state Current game state
    * @return Either error message or new game state after bot move
    */
  def playBotMove(bot: Bot, state: PositionState): Either[String, PositionState] = {
    for {
      move <- getBotMove(bot, state)
      newState <- GameService.applyMove(state, move)
    } yield newState
  }

  /** Create a bot by name
    *
    * @param botType Name of the bot type (e.g., "random", "capture")
    * @return Created bot instance
    */
  def createBot(botType: String): Bot = {
    botType.toLowerCase match {
      case "stockfish" | "uci" => new StockfishBotWrapper(depth = 15, skillLevel = 20)
      case "stockfish-easy" => new StockfishBotWrapper(depth = 10, skillLevel = 5)
      case "stockfish-medium" => new StockfishBotWrapper(depth = 12, skillLevel = 10)
      case _ => BotFactory.createBot(botType)
    }
  }

  /** Get list of available bot types */
  def availableBots: List[String] = {
    BotFactory.availableBots
  }
  
  /** Wrapper for Stockfish bot using UciBotService */
  private class StockfishBotWrapper(depth: Int, skillLevel: Int) extends Bot {
    private var uciBot: Option[UciBot] = None
    
    override def name: String = s"Stockfish (Skill $skillLevel)"
    override def difficulty: String = if (skillLevel <= 5) "Medium" else if (skillLevel <= 15) "Hard" else "Expert"
    override def description: String = s"Stockfish chess engine at depth $depth with skill level $skillLevel"
    
    private def ensureBot(state: PositionState): Unit = {
      if (uciBot.isEmpty) {
        uciBot = Some(new UciBot(depth, state))
      }
    }
    
    override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
      try {
        ensureBot(state)
        uciBot match {
          case Some(bot) => bot.selectMove(state, availableMoves)
          case None => availableMoves.head
        }
      } catch {
        case _: Exception => availableMoves.head
      }
    }
  }
  
  /** UCI Bot implementation using UciBotService */
  private class UciBot(depth: Int, initialState: PositionState) extends Bot {
    private var uciEngineInstance: Option[UciEngine] = None
    
    override def name: String = "UCI Engine Bot"
    override def difficulty: String = "Expert"
    override def description: String = "Bot backed by external UCI chess engine (e.g., Stockfish)"
    
    private def ensureEngine(): Unit = {
      if (uciEngineInstance.isEmpty) {
        EngineManager.initializeDefault()
        uciEngineInstance = EngineManager().getEngine("stockfish")
      }
    }
    
    override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
      try {
        ensureEngine()
        uciEngineInstance match {
          case Some(uciEngine) =>
            val fen = stateToFen(state)
            uciEngine.setPosition(fen)
            val bestMoveFuture = uciEngine.go(depth = depth, timeMs = 1000)
            val bestMove = Await.result(bestMoveFuture, 5.seconds)
            
            // Convert UCI move to internal Move
            uciToInternalMove(bestMove, state, availableMoves)
          case None =>
            availableMoves.head
        }
      } catch {
        case _: Exception => 
          availableMoves.head
      }
    }
    
    private def stateToFen(state: PositionState): String = {
      val boardStr = boardToFen(state.board)
      val turnStr = state.turn.toString.toLowerCase
      val castlingStr = "KQkq"
      val enPassantStr = "-"
      val halfMoveStr = state.halfmovesSinceLastCaptureOrPawn.toString
      val fullMoveStr = ((state.moveHistory.length / 2) + 1).toString
      
      s"$boardStr $turnStr $castlingStr $enPassantStr $halfMoveStr $fullMoveStr"
    }
    
    private def boardToFen(board: model.Board): String = {
      val fen = new StringBuilder()
      for (row <- 0 until 8) {
        var emptyCount = 0
        for (col <- 0 until 8) {
          board.pieceAt(model.Position(col, row)) match {
            case None => emptyCount += 1
            case Some(p) =>
              if (emptyCount > 0) {
                fen.append(emptyCount)
                emptyCount = 0
              }
              fen.append(pieceToFen(p))
          }
        }
        if (emptyCount > 0) fen.append(emptyCount)
        if (row < 7) fen.append("/")
      }
      fen.toString
    }
    
    private def pieceToFen(p: model.Piece): Char = {
      val base = p.pieceType match {
        case model.Pawn => 'p'
        case model.Knight => 'n'
        case model.Bishop => 'b'
        case model.Rook => 'r'
        case model.Queen => 'q'
        case model.King => 'k'
      }
      if (p.color == model.White) base.toUpper else base
    }
    
    private def uciToInternalMove(uciOutput: String, state: PositionState, availableMoves: List[Move]): Move = {
      // Extract bestmove from UCI output (which may include info lines)
      val lines = uciOutput.split("\n")
      val bestMoveLine = lines.find(_.startsWith("bestmove"))
      val uciMove = bestMoveLine match {
        case Some(line) => line.split(" ")(1)
        case None => return availableMoves.head
      }
      
      val legalMoves = GameService.getLegalMoves(state, state.turn)
      val fromCol = uciMove.charAt(0) - 'a'
      val fromRow = 8 - (uciMove.charAt(1) - '0')
      val toCol = uciMove.charAt(2) - 'a'
      val toRow = 8 - (uciMove.charAt(3) - '0')
      
      val from = model.Position(fromCol, fromRow)
      val to = model.Position(toCol, toRow)
      
      legalMoves.find(m => m.from == from && m.to == to).getOrElse(availableMoves.head)
    }
  }
}
