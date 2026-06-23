package inmemory

import dao.GameDao
import model.{Ongoing, PositionState}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable

/** In-memory implementation of GameDao for testing and lightweight deployments. */
class InMemoryGameDao(initialGames: Seq[PositionState] = Seq.empty)(implicit ec: ExecutionContext) extends GameDao {

  private val games: mutable.Map[String, PositionState] = mutable.Map.empty

  initialGames.foreach { game =>
    game.id.foreach(games(_) = game)
  }

  override def create(game: PositionState): Future[String] = Future {
    games.synchronized {
      val gameId = game.id.getOrElse(java.util.UUID.randomUUID().toString)
      games(gameId) = game.copy(id = Some(gameId))
      gameId
    }
  }

  override def findById(id: String): Future[Option[PositionState]] = Future {
    games.synchronized(games.get(id))
  }

  override def update(id: String, game: PositionState): Future[Boolean] = Future {
    games.synchronized {
      games.get(id) match {
        case Some(_) =>
          games(id) = game.copy(id = Some(id))
          true
        case None => false
      }
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    games.synchronized(games.remove(id).isDefined)
  }

  override def listAll(): Future[List[String]] = Future {
    games.synchronized(games.keys.toList)
  }

  override def findAll(): Future[List[PositionState]] = Future {
    games.synchronized(games.values.toList)
  }

  override def listSummaries(): Future[List[(String, String, Int, Boolean)]] = Future {
    games.synchronized {
      games.values.map { state =>
        val isGameOver = state.gameResult != Ongoing
        (state.id.getOrElse(""), state.turn.toString, state.moveHistory.length, isGameOver)
      }.toList
    }
  }

  override def findLatest(): Future[Option[PositionState]] = Future {
    games.synchronized {
      games.values.toList.sortBy(_.creationDate).reverse.headOption
    }
  }

  /** Clears all stored games. Useful between tests. */
  def clear(): Unit = games.synchronized {
    games.clear()
  }
}
