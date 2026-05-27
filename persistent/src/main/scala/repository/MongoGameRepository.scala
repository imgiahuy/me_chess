package repository

import dao.GameDao
import model.PositionState

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/** MongoDB-backed implementation of GameRepository.
  * 
  * This class wraps the async DAO layer and provides a synchronous interface
  * by blocking on Future results. This is suitable for the REST API which
  * currently uses synchronous repository methods.
  */
class MongoGameRepository(gameDao: GameDao)(implicit ec: ExecutionContext) extends GameRepository {

  private val timeout = Duration(5, "seconds")

  /** Creates a new game and returns its ID. */
  def createGame(initialState: PositionState): String = {
    Await.result(gameDao.create(initialState), timeout)
  }

  /** Retrieves a game by ID, or None if it doesn't exist. */
  def getGame(gameId: String): Option[PositionState] = {
    Await.result(gameDao.findById(gameId), timeout)
  }

  /** Updates a game state by ID. Returns true if successful, false if game not found. */
  def updateGame(gameId: String, newState: PositionState): Boolean = {
    Await.result(gameDao.update(gameId, newState), timeout)
  }

  /** Deletes a game session. */
  def deleteGame(gameId: String): Boolean = {
    Await.result(gameDao.delete(gameId), timeout)
  }

  /** Lists all active game IDs. */
  def listGames(): List[String] = {
    Await.result(gameDao.listAll(), timeout)
  }

  /** Lists all games. */
  def listAllGames(): List[PositionState] = {
    Await.result(gameDao.findAll(), timeout)
  }

  /** Loads the latest game from the database and returns its ID. */
  def loadLatestGame(): Either[String, String] = {
    Await.result(gameDao.findLatest(), timeout) match {
      case Some(state) =>
        state.id match {
          case Some(id) => Right(id)
          case None => Left("Latest game has no ID")
        }

      case None =>
        Left("No games found in database")
    }
  }
}
