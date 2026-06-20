package inmemory

import dao.{GameDao, GameEventDao, MoveDao, PlayerDao}

import scala.concurrent.{ExecutionContext, Future}

/** In-memory database manager that wires together in-memory DAOs.
  *
  * Useful for unit tests, local development, and lightweight deployments where
  * a real database is not required. Provides the same DAO interface as the
  * Slick/MongoDB managers, demonstrating the repository pattern abstraction.
  */
class InMemoryDatabase(
  val playerDao: PlayerDao,
  val moveDao: MoveDao,
  val gameDao: GameDao,
  val gameEventDao: GameEventDao
)(implicit ec: ExecutionContext) {

  /** No-op for the in-memory manager; data structures are already ready. */
  def initializeSchema(): Future[Unit] = Future.successful(())

  /** Resets all in-memory stores. */
  def resetSchema(): Future[Unit] = Future {
    playerDao match {
      case p: InMemoryPlayerDao => p.clear()
      case _ =>
    }
    moveDao match {
      case m: InMemoryMoveDao => m.clear()
      case _ =>
    }
    gameDao match {
      case g: InMemoryGameDao => g.clear()
      case _ =>
    }
    gameEventDao match {
      case e: InMemoryGameEventDao => e.clear()
      case _ =>
    }
  }

  /** No resources to close in the in-memory manager. */
  def close(): Future[Unit] = Future.successful(())
}

object InMemoryDatabase {

  /** Creates an in-memory database with empty stores. */
  def apply()(implicit ec: ExecutionContext): InMemoryDatabase = {
    val playerDao = new InMemoryPlayerDao()
    val moveDao = new InMemoryMoveDao()
    val gameDao = new InMemoryGameDao()
    val gameEventDao = new InMemoryGameEventDao()
    new InMemoryDatabase(playerDao, moveDao, gameDao, gameEventDao)
  }
}
