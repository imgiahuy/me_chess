package analysis

import model._
import scala.collection.mutable.ListBuffer

/** Analysis data for a single move */
case class MoveAnalysis(
  moveNumber: Int,
  move: Move,
  player: Color,
  timestamp: Long,
  timeTakenMs: Long,
  evaluationBefore: Option[Double] = None,
  evaluationAfter: Option[Double] = None,
  isBlunder: Boolean = false,
  isMistake: Boolean = false,
  isInaccuracy: Boolean = false
)

/** Analysis data for a complete game */
case class GameAnalysis(
  gameId: String,
  moves: List[MoveAnalysis],
  totalBlunders: Int,
  totalMistakes: Int,
  totalInaccuracies: Int,
  averageMoveTimeMs: Double,
  longestMoveTimeMs: Long,
  shortestMoveTimeMs: Long
)

/** Service for analyzing game moves and detecting blunders/mistakes */
class MoveAnalysisService {
  
  /** Analyze a game's move history */
  def analyzeGame(gameId: String, state: PositionState, moveTimestamps: List[Long]): GameAnalysis = {
    val moves = state.moveHistory
    val moveAnalyses = new ListBuffer[MoveAnalysis]()
    
    var totalTimeMs = 0L
    var longestMoveTimeMs = 0L
    var shortestMoveTimeMs = Long.MaxValue
    
    for (i <- moves.indices) {
      val move = moves(i)
      val player = if (i % 2 == 0) White else Black
      val timestamp = if (i < moveTimestamps.length) moveTimestamps(i) else System.currentTimeMillis()
      val prevTimestamp = if (i > 0 && i - 1 < moveTimestamps.length) moveTimestamps(i - 1) else timestamp - 1000
      val timeTakenMs = math.max(1, timestamp - prevTimestamp)
      
      totalTimeMs += timeTakenMs
      longestMoveTimeMs = math.max(longestMoveTimeMs, timeTakenMs)
      shortestMoveTimeMs = math.min(shortestMoveTimeMs, timeTakenMs)
      
      // Basic move analysis (blunder/mistake detection would require engine evaluation)
      val analysis = MoveAnalysis(
        moveNumber = i + 1,
        move = move,
        player = player,
        timestamp = timestamp,
        timeTakenMs = timeTakenMs,
        evaluationBefore = None, // Would need engine evaluation
        evaluationAfter = None,  // Would need engine evaluation
        isBlunder = false,      // Would need evaluation comparison
        isMistake = false,      // Would need evaluation comparison
        isInaccuracy = false    // Would need evaluation comparison
      )
      
      moveAnalyses += analysis
    }
    
    val avgMoveTimeMs = if (moves.nonEmpty) totalTimeMs.toDouble / moves.length else 0.0
    val finalShortestTime = if (shortestMoveTimeMs == Long.MaxValue) 0L else shortestMoveTimeMs
    
    GameAnalysis(
      gameId = gameId,
      moves = moveAnalyses.toList,
      totalBlunders = 0,
      totalMistakes = 0,
      totalInaccuracies = 0,
      averageMoveTimeMs = avgMoveTimeMs,
      longestMoveTimeMs = longestMoveTimeMs,
      shortestMoveTimeMs = finalShortestTime
    )
  }
  
  /** Detect blunders based on evaluation change (requires engine evaluation) */
  def detectBlunder(evalBefore: Double, evalAfter: Double, player: Color): Boolean = {
    val evalChange = if (player == White) evalAfter - evalBefore else evalBefore - evalAfter
    evalChange < -3.0 // Loss of more than 3 pawns is considered a blunder
  }
  
  /** Detect mistakes based on evaluation change */
  def detectMistake(evalBefore: Double, evalAfter: Double, player: Color): Boolean = {
    val evalChange = if (player == White) evalAfter - evalBefore else evalBefore - evalAfter
    evalChange < -1.5 && evalChange >= -3.0 // Loss of 1.5-3 pawns is a mistake
  }
  
  /** Detect inaccuracies based on evaluation change */
  def detectInaccuracy(evalBefore: Double, evalAfter: Double, player: Color): Boolean = {
    val evalChange = if (player == White) evalAfter - evalBefore else evalBefore - evalAfter
    evalChange < -0.5 && evalChange >= -1.5 // Loss of 0.5-1.5 pawns is an inaccuracy
  }
}

/** Singleton move analysis service */
object MoveAnalysisService {
  private var instance: Option[MoveAnalysisService] = None
  
  def apply(): MoveAnalysisService = {
    instance.getOrElse {
      val service = new MoveAnalysisService()
      instance = Some(service)
      service
    }
  }
}
