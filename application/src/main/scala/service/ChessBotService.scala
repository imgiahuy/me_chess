package service

import model.{PositionState, Bot, BotFactory, Move}
import engine.{UciBotService, UciEngine, EngineManager}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/** Service for handling bot moves and interactions */
object ChessBotService {

  private val logger = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val uciBotService = UciBotService()

  // Cache for bot instances to reuse engines across moves
  private val botCache = scala.collection.mutable.Map[String, Bot]()

  // Initialize UCI bot service on load
  uciBotService.initialize()
  logger.info("BotService initialized with UCI bot service")

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
    val cacheKey = botType.toLowerCase
    logger.info(s"Creating bot of type: $botType (cache hit: ${botCache.contains(cacheKey)})")
    
    botCache.getOrElseUpdate(cacheKey, {
      cacheKey match {
        case "stockfish" | "uci" => new StockfishBotWrapper(engineName = "stockfish", depth = 15, skillLevel = 20)
        case "stockfish-easy" => new StockfishBotWrapper(engineName = "stockfish-easy", depth = 10, skillLevel = 5)
        case "stockfish-medium" => new StockfishBotWrapper(engineName = "stockfish-medium", depth = 12, skillLevel = 10)
        case _ =>
          logger.info(s"Creating non-Stockfish bot: $botType")
          BotFactory.createBot(botType)
      }
    })
  }

  /** Get list of available bot types */
  def availableBots: List[String] = {
    BotFactory.availableBots ++ List("stockfish", "stockfish-easy", "stockfish-medium")
  }
  
  /** Wrapper for Stockfish bot using UciBotService */
  private class StockfishBotWrapper(engineName: String, depth: Int, skillLevel: Int) extends Bot {
    private val logger = LoggerFactory.getLogger(getClass)
    // Reuse the same UciBot instance across moves to maintain engine state
    private val uciBot = new UciBot(engineName, depth)

    override def name: String = s"Stockfish (Skill $skillLevel)"
    override def difficulty: String = if (skillLevel <= 5) "Medium" else if (skillLevel <= 15) "Hard" else "Expert"
    override def description: String = s"Stockfish chess engine at depth $depth with skill level $skillLevel"

    override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
      try {
        logger.info(s"StockfishBotWrapper.selectMove called with ${availableMoves.length} available moves")
        uciBot.selectMove(state, availableMoves)
      } catch {
        case e: Exception =>
          logger.error(s"Error in StockfishBotWrapper.selectMove: ${e.getMessage}", e)
          availableMoves.head
      }
    }
  }
  
  /** UCI Bot implementation using UciBotService */
  private class UciBot(engineName: String, depth: Int) extends Bot {
    private val logger = LoggerFactory.getLogger(getClass)
    private var uciEngineInstance: Option[UciEngine] = None

    override def name: String = "UCI Engine Bot"
    override def difficulty: String = "Expert"
    override def description: String = "Bot backed by external UCI chess engine (e.g., Stockfish)"

    private def ensureEngine(): Unit = {
      logger.info(s"ensureEngine called for: $engineName")
      EngineManager.initializeDefault()
      // Check if current engine instance is still running, if not, clear it
      if (uciEngineInstance.isDefined && !EngineManager().isEngineRunning(engineName)) {
        logger.warn(s"Engine $engineName stopped, clearing instance")
        uciEngineInstance = None
      }
      // Get or start the engine
      if (uciEngineInstance.isEmpty) {
        logger.info(s"Getting or starting engine: $engineName")
        uciEngineInstance = EngineManager().getEngine(engineName).orElse {
          // Try to start the engine if not running
          logger.info(s"Engine not running, attempting to start: $engineName")
          EngineManager().startEngine(engineName).toOption
        }
        if (uciEngineInstance.isDefined) {
          logger.info(s"Engine $engineName started successfully")
        } else {
          logger.error(s"Failed to start engine: $engineName")
        }
      }
    }
    
    override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
      try {
        logger.info(s"UciBot.selectMove called for engine: $engineName")
        ensureEngine()
        uciEngineInstance match {
          case Some(uciEngine) =>
            // Get configured time from EngineManager
            val config = EngineManager().getEngineConfig(engineName)
            val timeMs = config.map(_.defaultTimeMs).getOrElse(5000L)
            logger.info(s"Using UCI engine $engineName for move calculation (depth=$depth, timeMs=$timeMs)")
            val fen = stateToFen(state)
            uciEngine.setPosition(fen)
            val bestMoveFuture = uciEngine.go(depth = depth, timeMs = timeMs)
            val bestMove = Await.result(bestMoveFuture, timeMs.milliseconds + 5.seconds)
            logger.info(s"UCI engine $engineName returned move: $bestMove")

            // Convert UCI move to internal Move
            uciToInternalMove(bestMove, state, availableMoves)
          case None =>
            logger.error(s"Engine $engineName not available, falling back to first legal move")
            availableMoves.head
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error using UCI engine $engineName: ${e.getMessage}, falling back to first legal move", e)
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
