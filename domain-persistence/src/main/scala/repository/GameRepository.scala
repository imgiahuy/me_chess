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
