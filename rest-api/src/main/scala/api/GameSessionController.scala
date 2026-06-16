package api

import controller.GameControllerInterface
import model.{PositionState, Color}
import repository.GameRepository
import service.{GameService, BotService}
import kafka.{KafkaGameEventService, GameEvent}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class GameSessionController(
                             controller: GameControllerInterface,
                             repo: GameRepository,
                             kafkaService: Option[KafkaGameEventService] = None
                           )(implicit ec: ExecutionContext) {

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

  /** Create a new game session with player names and time control */
  def createGameWithTimeControl(whitePlayer: String, blackPlayer: String, timeControl: model.TimeControl): String = {
    val initialState = controller.createWithTimeControl(whitePlayer, blackPlayer, timeControl)
    val gameId = repo.createGame(initialState)

    // Publish GameCreated event to Kafka
    kafkaService.foreach { service =>
      service.publishGameCreated(gameId, whitePlayer, blackPlayer, initialState)
        .recover { case ex => println(s"[Kafka] Failed to publish game created: ${ex.getMessage}") }
    }

    gameId
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

        // Publish MoveMade event to Kafka
        kafkaService.foreach { service =>
          val moveNumber = newState.moveHistory.length
          service.publishMoveMade(gameId, newState, input, moveNumber)
            .recover { case ex => println(s"[Kafka] Failed to publish move: ${ex.getMessage}") }

          // If game ended, publish GameEnded event
          if (newState.gameResult != model.Ongoing) {
            service.publishGameEnded(gameId, newState)
              .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
          }
        }

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

        // Publish PlayerResigned and GameEnded events to Kafka
        kafkaService.foreach { service =>
          val colorStr = color.toString.toLowerCase
          service.publishPlayerResigned(gameId, colorStr)
            .recover { case ex => println(s"[Kafka] Failed to publish resignation: ${ex.getMessage}") }

          service.publishGameEnded(gameId, newState)
            .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
        }

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

      // Publish bot move event to Kafka
      kafkaService.foreach { service =>
        val lastMove = newState.moveHistory.lastOption
        val moveUci = lastMove.map(formatter.UciFormatter.moveToUci).getOrElse("")
        val moveNumber = newState.moveHistory.length

        service.publishMoveMade(gameId, newState, moveUci, moveNumber)
          .recover { case ex => println(s"[Kafka] Failed to publish bot move: ${ex.getMessage}") }

        // If game ended, publish GameEnded event
        if (newState.gameResult != model.Ongoing) {
          service.publishGameEnded(gameId, newState)
            .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
        }
      }

      newState
    }
  }

  /** Get available bot types */
  def getAvailableBots(): List[String] = {
    BotService.availableBots
  }

  /** Get available bot types with metadata */
  def getAvailableBotInfos(): List[model.BotInfo] = {
    model.BotFactory.availableBotInfo
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

  /** Check and apply timeout if a player's time has expired */
  def checkAndApplyTimeout(gameId: String): Either[String, PositionState] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        if (state.gameResult != model.Ongoing) {
          // Game already over, just return current state
          Right(state)
        } else {
          // Check if current player's time has expired using real-time calculation
          val currentPlayer = state.turn
          val isTimeExpired = currentPlayer match {
            case model.White => state.whiteTime.exists(_.isTimeOver)
            case model.Black => state.blackTime.exists(_.isTimeOver)
          }

          if (isTimeExpired) {
            val winner = currentPlayer.opposite
            val newState = state.copy(gameResult = model.TimeOut(winner))
            repo.updateGame(gameId, newState)

            // Publish timeout and game ended events to Kafka
            kafkaService.foreach { service =>
              val remainingTime = 0L
              service.publishTimeWarning(gameId, currentPlayer.toString.toLowerCase, remainingTime, isTimeout = true)
                .recover { case ex => println(s"[Kafka] Failed to publish timeout: ${ex.getMessage}") }

              service.publishGameEnded(gameId, newState)
                .recover { case ex => println(s"[Kafka] Failed to publish game ended: ${ex.getMessage}") }
            }

            Right(newState)
          } else {
            Right(state)
          }
        }
      case None =>
        Left("Game not found")
    }
  }
}
