package model

/** Base trait for chess bots */
trait Bot {
  def name: String
  def difficulty: String
  
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
  
  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) throw new IllegalArgumentException("No legal moves available")
    availableMoves(scala.util.Random.nextInt(availableMoves.length))
  }
}

/** Bot that prefers capturing pieces and center control */
class CapturePreferenceBot extends Bot {
  override def name: String = "Capture Bot"
  override def difficulty: String = "Medium"
  
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

/** Factory for creating bots */
object BotFactory {
  def createBot(botType: String): Bot = botType.toLowerCase match {
    case "random" => new RandomBot()
    case "capture" => new CapturePreferenceBot()
    case _ => new RandomBot()
  }
  
  def availableBots: List[String] = List("random", "capture")
}

