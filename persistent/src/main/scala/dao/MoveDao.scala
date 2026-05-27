package dao

import model.Move

import scala.concurrent.Future

/** Data Access Object interface for Move entities.
  * 
  * This interface abstracts the database implementation, allowing
  * different persistence mechanisms to be swapped without changing
  * the application code.
  */
trait MoveDao {

  /** Creates a new move and returns its ID. */
  def create(move: Move, gameId: String, moveIndex: Int): Future[Int]

  /** Retrieves a move by ID. */
  def findById(id: Int): Future[Option[Move]]

  /** Retrieves all moves for a specific game, in order. */
  def findByGameId(gameId: String): Future[List[Move]]

  /** Deletes all moves for a specific game. */
  def deleteByGameId(gameId: String): Future[Int]
}
