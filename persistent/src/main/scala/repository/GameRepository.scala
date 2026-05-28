package repository

import model.PositionState

/** Repository interface for game storage.
  * Defines the contract for storing and retrieving game states.
  */
trait GameRepository {
  /** Creates a new game session with a random ID and returns the ID. */
  def createGame(initialState: PositionState): String

  /** Retrieves a game by ID, or None if it doesn't exist. */
  def getGame(gameId: String): Option[PositionState]

  /** Updates a game state by ID. Returns true if successful, false if game not found. */
  def updateGame(gameId: String, newState: PositionState): Boolean

  /** Deletes a game session. */
  def deleteGame(gameId: String): Boolean

  /** Lists all active game IDs. */
  def listGames(): List[String]

  /** Gets lightweight summaries of all games without full state. */
  def getGameSummaries(): List[(String, String, Int, Boolean)]

  /** Loads the latest game from the database and returns its ID. */
  def loadLatestGame(): Either[String, String]
}

/** In-memory game session manager for REST API.
 *
 * Stores active games by session ID. In production, this would be replaced
 * with a persistent database and session management system.
 */
class InMemoryGameRepository extends GameRepository {

  import scala.collection.mutable

  private val games = mutable.Map[String, PositionState]()

  /** Creates a new game session with a random ID and returns the ID. */
  def createGame(initialState: PositionState): String = {
    val gameId = java.util.UUID.randomUUID().toString
    games(gameId) = initialState
    gameId
  }

  /** Retrieves a game by ID, or None if it doesn't exist. */
  def getGame(gameId: String): Option[PositionState] = games.get(gameId)

  /** Updates a game state by ID. Returns true if successful, false if game not found. */
  def updateGame(gameId: String, newState: PositionState): Boolean = {
    if (games.contains(gameId)) {
      games(gameId) = newState
      true
    } else {
      false
    }
  }

  /** Deletes a game session. */
  def deleteGame(gameId: String): Boolean = games.remove(gameId).isDefined

  /** Lists all active game IDs. */
  def listGames(): List[String] = games.keys.toList

  /** Gets lightweight summaries of all games without full state. */
  def getGameSummaries(): List[(String, String, Int, Boolean)] = {
    games.map { case (gameId, state) =>
      (gameId, state.turn.toString, state.moveHistory.length, service.GameService.isGameOver(state))
    }.toList
  }

  /** Loads the latest game from the database and returns its ID. */
  def loadLatestGame(): Either[String, String] = Left("Not supported in in-memory repository")
}

