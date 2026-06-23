package inmemory

import dao.PlayerDao
import model.Player

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicInteger

/** In-memory implementation of PlayerDao for testing and lightweight deployments. */
class InMemoryPlayerDao(initialPlayers: Seq[Player] = Seq.empty)(implicit ec: ExecutionContext) extends PlayerDao {

  private val players: mutable.Map[Int, Player] = mutable.Map.empty
  private val nameToId: mutable.Map[String, Int] = mutable.Map.empty
  private val eloMap: mutable.Map[Int, Int] = mutable.Map.empty
  private val nextId: AtomicInteger = new AtomicInteger(1)

  initialPlayers.zipWithIndex.foreach { case (player, idx) =>
    val id = idx + 1
    players(id) = player
    nameToId(player.name) = id
    eloMap(id) = 1200
  }

  override def create(player: Player): Future[Int] = Future {
    nameToId.get(player.name) match {
      case Some(id) => id
      case None =>
        val id = nextId.getAndIncrement()
        players.synchronized {
          players(id) = player
          nameToId(player.name) = id
          eloMap(id) = 1200
        }
        id
    }
  }

  override def findById(id: Int): Future[Option[Player]] = Future {
    players.synchronized(players.get(id))
  }

  override def findByName(name: String): Future[Option[Player]] = Future {
    players.synchronized(nameToId.get(name).flatMap(players.get))
  }

  override def update(id: Int, player: Player): Future[Boolean] = Future {
    players.synchronized {
      players.get(id) match {
        case Some(old) =>
          nameToId.remove(old.name)
          players(id) = player
          nameToId(player.name) = id
          true
        case None => false
      }
    }
  }

  override def delete(id: Int): Future[Boolean] = Future {
    players.synchronized {
      players.remove(id) match {
        case Some(player) =>
          nameToId.remove(player.name)
          true
        case None => false
      }
    }
  }

  override def findAll(): Future[List[Player]] = Future {
    players.synchronized(players.values.toList)
  }

  override def getOrCreateId(name: String): Future[Int] = Future {
    nameToId.get(name) match {
      case Some(id) => id
      case None =>
        val id = nextId.getAndIncrement()
        players.synchronized {
          players(id) = Player(name)
          nameToId(name) = id
          eloMap(id) = 1200
        }
        id
    }
  }

  override def updateElo(id: Int, newElo: Int): Future[Boolean] = Future {
    players.synchronized {
      if (players.contains(id)) { eloMap(id) = newElo; true } else false
    }
  }

  override def findLeaderboard(): Future[List[(Player, Int)]] = Future {
    players.synchronized {
      players.toList
        .map { case (id, player) => (player, eloMap.getOrElse(id, 1200)) }
        .sortBy(-_._2)
    }
  }

  /** Clears all stored players. Useful between tests. */
  def clear(): Unit = players.synchronized {
    players.clear()
    nameToId.clear()
    eloMap.clear()
  }
}
