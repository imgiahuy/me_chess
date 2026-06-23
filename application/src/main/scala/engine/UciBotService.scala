package engine

import model._
import service.GameService
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

/** Service for creating UCI engine-backed bots without circular dependencies */
class UciBotService()(implicit ec: ExecutionContext) {
  
  private val engineManager = EngineManager()
  
  /** Initialize default engine configurations */
  def initialize(): Unit = {
    EngineManager.initializeDefault()
  }
  
  /** Create a bot backed by a UCI engine */
  def createUciBot(engineName: String, gameState: PositionState): Future[Bot] = {
    Future {
      try {
        val engine = engineManager.getEngine(engineName).getOrElse {
          engineManager.startEngine(engineName).getOrElse {
            throw new RuntimeException(s"Failed to start engine: $engineName")
          }
        }
        
        // Create a bot that will use the engine for each move
        new UciBot(engine, gameState)
      } catch {
        case e: Exception => throw new RuntimeException(s"Failed to create UCI bot: ${e.getMessage}", e)
      }
    }
  }
  
  /** Check if a UCI bot is available */
  def isUciBotAvailable(engineName: String): Boolean = {
    engineManager.getEngineConfig(engineName).isDefined
  }
  
  /** Get available UCI bot names */
  def availableUciBots: List[String] = {
    engineManager.listEngineConfigs.map(_._1)
  }
  
  /** Convert PositionState to FEN string */
  private def gameStateToFen(state: PositionState): String = {
    val boardStr = boardToFen(state.board)
    val turnStr = state.turn.toString.toLowerCase
    val castlingStr = getCastlingRights(state)
    val enPassantStr = getEnPassantSquare(state)
    val halfMoveStr = state.halfmovesSinceLastCaptureOrPawn.toString
    val fullMoveStr = ((state.moveHistory.length / 2) + 1).toString
    
    s"$boardStr $turnStr $castlingStr $enPassantStr $halfMoveStr $fullMoveStr"
  }
  
  /** Convert board to FEN string */
  private def boardToFen(board: Board): String = {
    val fen = new StringBuilder()
    for (row <- 0 until 8) {
      var emptyCount = 0
      for (col <- 0 until 8) {
        board.pieceAt(Position(col, row)) match {
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
  
  /** Convert piece to FEN character */
  private def pieceToFen(p: Piece): Char = {
    val base = p.pieceType match {
      case Pawn => 'p'
      case Knight => 'n'
      case Bishop => 'b'
      case Rook => 'r'
      case Queen => 'q'
      case King => 'k'
    }
    if (p.color == White) base.toUpper else base
  }
  
  /** Get castling rights */
  private def getCastlingRights(state: PositionState): String = {
    // Simplified - would need to track actual castling rights
    "KQkq"
  }
  
  /** Get en passant square */
  private def getEnPassantSquare(state: PositionState): String = {
    // Simplified - would need to track actual en passant
    "-"
  }
}

/** Bot implementation backed by a UCI engine */
class UciBot(engine: UciEngine, initialState: PositionState) extends Bot {
  
  override def name: String = "UCI Engine Bot"
  override def difficulty: String = "Expert"
  override def description: String = "Bot backed by external UCI chess engine (e.g., Stockfish)"
  
  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    try {
      val fen = gameStateToFen(state)
      engine.setPosition(fen)
      val bestMoveFuture = engine.go(depth = 15, timeMs = 1000)
      val bestMove = Await.result(bestMoveFuture, 5.seconds)
      
      // Convert UCI move to internal Move
      uciToInternalMove(bestMove, state)
    } catch {
      case _: Exception => 
        // Fallback to random move if engine fails
        availableMoves.head
    }
  }
  
  private def gameStateToFen(state: PositionState): String = {
    val boardStr = boardToFen(state.board)
    val turnStr = state.turn.toString.toLowerCase
    val castlingStr = getCastlingRights(state)
    val enPassantStr = getEnPassantSquare(state)
    val halfMoveStr = state.halfmovesSinceLastCaptureOrPawn.toString
    val fullMoveStr = ((state.moveHistory.length / 2) + 1).toString
    
    s"$boardStr $turnStr $castlingStr $enPassantStr $halfMoveStr $fullMoveStr"
  }
  
  /** Convert board to FEN string */
  private def boardToFen(board: Board): String = {
    val fen = new StringBuilder()
    for (row <- 0 until 8) {
      var emptyCount = 0
      for (col <- 0 until 8) {
        board.pieceAt(Position(col, row)) match {
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
  
  /** Convert piece to FEN character */
  private def pieceToFen(p: Piece): Char = {
    val base = p.pieceType match {
      case Pawn => 'p'
      case Knight => 'n'
      case Bishop => 'b'
      case Rook => 'r'
      case Queen => 'q'
      case King => 'k'
    }
    if (p.color == White) base.toUpper else base
  }
  
  /** Get castling rights */
  private def getCastlingRights(state: PositionState): String = {
    "KQkq"
  }
  
  /** Get en passant square */
  private def getEnPassantSquare(state: PositionState): String = {
    "-"
  }
  
  private def uciToInternalMove(uciMove: String, state: PositionState): Move = {
    val legalMoves = GameService.getLegalMoves(state, state.turn)
    val fromCol = uciMove.charAt(0) - 'a'
    val fromRow = 8 - (uciMove.charAt(1) - '0')
    val toCol = uciMove.charAt(2) - 'a'
    val toRow = 8 - (uciMove.charAt(3) - '0')
    
    val from = Position(fromCol, fromRow)
    val to = Position(toCol, toRow)
    
    legalMoves.find(m => m.from == from && m.to == to).getOrElse(legalMoves.head)
  }
  
  /** Shutdown the engine when bot is no longer needed */
  def shutdown(): Unit = {
    engine.shutdown()
  }
}

/** Singleton UCI bot service */
object UciBotService {
  private var instance: Option[UciBotService] = None
  
  def apply()(implicit ec: ExecutionContext): UciBotService = {
    instance.getOrElse {
      val service = new UciBotService()
      instance = Some(service)
      service
    }
  }
  
  def initialize()(implicit ec: ExecutionContext): Unit = {
    apply().initialize()
  }
}
