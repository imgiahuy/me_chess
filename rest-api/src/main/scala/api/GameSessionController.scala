package api

import controller.GameControllerInterface
import model.{PositionState, Color}
import repository.GameRepository
import service.{GameService, BotService}

class GameSessionController(
                             controller: GameControllerInterface,
                             repo: GameRepository
                           ) {

  /** Create a new game session and return its ID */
  def createGame(): String = {
    val initialState = controller.create("White", "Black")
    repo.createGame(initialState)
  }

  /** Create a new game session with player names and return its ID */
  def createGame(whitePlayer: String, blackPlayer: String): String = {
    val initialState = controller.create(whitePlayer, blackPlayer)
    repo.createGame(initialState)
  }

  /** Get a game by ID */
  def getGame(gameId: String): Option[PositionState] =
    repo.getGame(gameId)

  /** Apply a move using gameId */
  def makeMove(gameId: String, input: String): Either[String, PositionState] = {
    if (input.trim.isEmpty) {
      Left("Move input cannot be empty")
    } else {
      for {
        state    <- repo.getGame(gameId).toRight("Game not found")
        newState <- controller.makeMove(state, input)
      } yield {
        repo.updateGame(gameId, newState)
        newState
      }
    }
  }

  /** Player resigns from game */
  def resign(gameId: String, color: Color): PositionState = {
    repo.getGame(gameId) match {
      case Some(state) =>
        val newState = GameService.resign(state, color)
        repo.updateGame(gameId, newState)
        newState
      case None =>
        throw new Exception("Game not found")
    }
  }

  /** Play a bot move */
  def playBotMove(gameId: String, botType: String): Either[String, PositionState] = {
    for {
      state <- repo.getGame(gameId).toRight("Game not found")
      bot = BotService.createBot(botType)
      newState <- BotService.playBotMove(bot, state)
    } yield {
      repo.updateGame(gameId, newState)
      newState
    }
  }

  /** Get available bot types */
  def getAvailableBots(): List[String] = {
    BotService.availableBots
  }

  /** Delete a game session */
  def deleteGame(gameId: String): Boolean =
    repo.deleteGame(gameId)

  /** List all active games */
  def listGames(): List[String] =
    repo.listGames()

  /** Get lightweight summaries of all games */
  def getGameSummaries(): List[(String, String, Int, Boolean)] =
    repo.getGameSummaries()

  /** Save a specific game to the database */
  def saveGame(gameId: String): Either[String, Unit] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        try {
          repo.updateGame(gameId, state)
          Right(())
        } catch {
          case e: Exception => Left(s"Failed to save game: ${e.getMessage}")
        }
      case None =>
        Left("Game not found")
    }
  }

  /** Load a game from the database by ID and create a new session for it */
  def loadGame(gameId: String): Either[String, String] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        state.id match {
          case Some(existingId) => Right(existingId)
          case None =>
            try {
              Right(repo.createGame(state))
            } catch {
              case e: Exception => Left(s"Failed to load game: ${e.getMessage}")
            }
        }
      case None =>
        Left("Game not found")
    }
  }

  /** Load the latest game from the database and create a new session for it */
  def loadLatestGame(): Either[String, String] = {
    repo.loadLatestGame()
  }

  /** Check if a game is over */
  def isGameOver(gameId: String): Either[String, Boolean] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(GameService.isGameOver(state))
      case None => Left("Game not found")
    }
  }

  /** Update a game state by ID */
  def updateGame(gameId: String, newState: PositionState): Unit = repo.updateGame(gameId, newState)

  /** Get the winner of a game, if any */
  def getWinner(gameId: String): Either[String, Option[String]] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(GameService.winner(state).map(_.toString))
      case None => Left("Game not found")
    }
  }
  
  /** Get the current turn for a game */
  def getCurrentTurn(gameId: String): Either[String, String] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(state.turn.toString)
      case None => Left("Game not found")
    }
  }
  
  /** Get move count for a game */
  def getMoveCount(gameId: String): Either[String, Int] = {
    repo.getGame(gameId) match {
      case Some(state) => Right(state.moveHistory.length)
      case None => Left("Game not found")
    }
  }
  
  /** Get game statistics */
  def getGameStats(gameId: String): Either[String, (Int, String, Boolean, Option[String])] = {
    repo.getGame(gameId) match {
      case Some(state) => 
        Right((state.moveHistory.length, state.turn.toString, GameService.isGameOver(state), GameService.winner(state).map(_.toString)))
      case None => 
        Left("Game not found")
    }
  }
  
  /** Export game to PGN format */
  def exportToPgn(gameId: String, event: String, site: String): String = {
    repo.getGame(gameId) match {
      case Some(state) =>
        controller.exportToPgn(state, event, site, s"game_${gameId}.pgn")
        // Return the PGN content directly for the API response
        service.GameService.exportToPgn(state, event, site)
      case None =>
        throw new Exception("Game not found")
    }
  }
}
