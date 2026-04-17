package rest

import domain.engine.GameState
import domain.model.Move
import scala.collection.mutable

/** In-memory game session manager for REST API.
 *
 * Stores active games by session ID. In production, this would be replaced
 * with a persistent database and session management system.
 */
class GameRepository {

  private val games = mutable.Map[String, GameState]()

  /** Creates a new game session with a random ID and returns the ID. */
  def createGame(): String = {
    val gameId = java.util.UUID.randomUUID().toString
    games(gameId) = GameState.initial
    gameId
  }

  /** Retrieves a game by ID, or None if it doesn't exist. */
  def getGame(gameId: String): Option[GameState] = games.get(gameId)

  /** Updates a game state by ID. Returns true if successful, false if game not found. */
  def updateGame(gameId: String, newState: GameState): Boolean = {
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
}

