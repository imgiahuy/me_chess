package inmemory

import dao.MoveDao
import model.{Move, Position}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicInteger

/** In-memory implementation of MoveDao for testing and lightweight deployments. */
class InMemoryMoveDao(initialMoves: Seq[(Move, String, Int)] = Seq.empty)(implicit ec: ExecutionContext) extends MoveDao {

  private val moves: mutable.Map[Int, (Move, String, Int)] = mutable.Map.empty
  private val nextId: AtomicInteger = new AtomicInteger(1)

  initialMoves.zipWithIndex.foreach { case ((move, gameId, moveIndex), idx) =>
    moves(idx + 1) = (move, gameId, moveIndex)
  }

  override def create(move: Move, gameId: String, moveIndex: Int): Future[Int] = Future {
    moves.synchronized {
      val id = nextId.getAndIncrement()
      moves(id) = (move, gameId, moveIndex)
      id
    }
  }

  override def findById(id: Int): Future[Option[Move]] = Future {
    moves.synchronized(moves.get(id).map(_._1))
  }

  override def findByGameId(gameId: String): Future[List[Move]] = Future {
    moves.synchronized {
      moves.values
        .filter(_._2 == gameId)
        .toList
        .sortBy(_._3)
        .map(_._1)
    }
  }

  override def deleteByGameId(gameId: String): Future[Int] = Future {
    moves.synchronized {
      val ids = moves.filter(_._2._2 == gameId).keys.toList
      ids.foreach(moves.remove)
      ids.size
    }
  }

  /** Clears all stored moves. Useful between tests. */
  def clear(): Unit = moves.synchronized {
    moves.clear()
  }
}
