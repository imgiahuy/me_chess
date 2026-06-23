package service

import model.{PositionState, Bot, BotFactory, Move}

/** Service for handling bot moves and interactions */
object BotService {

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
    BotFactory.createBot(botType)
  }

  /** Get list of available bot types */
  def availableBots: List[String] = {
    BotFactory.availableBots
  }
}
