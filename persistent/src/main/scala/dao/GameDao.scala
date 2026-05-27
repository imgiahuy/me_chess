package dao

import model.PositionState

import scala.concurrent.Future

/** Data Access Object interface for Game entities.
  * 
  * This interface abstracts the database implementation, allowing
  * different persistence mechanisms (Slick, JDBC, NoSQL, etc.)
  * to be swapped without changing the application code.
  */
trait GameDao {

  /** Creates a new game and returns its ID. */
  def create(game: PositionState): Future[String]

  /** Retrieves a game by ID. */
  def findById(id: String): Future[Option[PositionState]]

  /** Updates an existing game. */
  def update(id: String, game: PositionState): Future[Boolean]

  /** Deletes a game by ID. */
  def delete(id: String): Future[Boolean]

  /** Lists all game IDs. */
  def listAll(): Future[List[String]]

  /** Lists all games. */
  def findAll(): Future[List[PositionState]]

  /** Finds the latest game by creation date. */
  def findLatest(): Future[Option[PositionState]]
}
