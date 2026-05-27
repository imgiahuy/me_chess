package dao

import model.Player

import scala.concurrent.Future

/** Data Access Object interface for Player entities.
  * 
  * This interface abstracts the database implementation, allowing
  * different persistence mechanisms to be swapped without changing
  * the application code.
  */
trait PlayerDao {

  /** Creates a new player and returns its ID. */
  def create(player: Player): Future[Int]

  /** Retrieves a player by ID. */
  def findById(id: Int): Future[Option[Player]]

  /** Retrieves a player by name. */
  def findByName(name: String): Future[Option[Player]]

  /** Updates an existing player. */
  def update(id: Int, player: Player): Future[Boolean]

  /** Deletes a player by ID. */
  def delete(id: Int): Future[Boolean]

  /** Lists all players. */
  def findAll(): Future[List[Player]]

  /** Gets or creates a player ID by name. Returns the database ID. */
  def getOrCreateId(name: String): Future[Int]
}
