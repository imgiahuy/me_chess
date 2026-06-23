package redis

import dao.PlayerDao
import model.Player
import redis.clients.jedis.JedisPool
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

/** uPickle ReadWriter for the simple Player case class. */
given ReadWriter[Player] = macroRW[Player]

/** PlayerDao decorator that caches name lookups in Redis.
  *
  * Cache entries are stored as JSON with a configurable TTL. Write operations
  * invalidate the cached entry for that player name so reads never return stale
  * data after an update.
  */
class CachedPlayerDao(
  underlying: PlayerDao,
  pool: JedisPool,
  ttlSeconds: Int = 300
)(implicit ec: ExecutionContext) extends PlayerDao {

  private def cacheKey(name: String): String = s"player:name:$name"

  override def create(player: Player): Future[Int] = {
    underlying.create(player).andThen { _ =>
      invalidate(player.name)
    }
  }

  override def findById(id: Int): Future[Option[Player]] = {
    underlying.findById(id)
  }

  override def findByName(name: String): Future[Option[Player]] = {
    withCache(name) {
      underlying.findByName(name)
    }
  }

  override def update(id: Int, player: Player): Future[Boolean] = {
    underlying.update(id, player).andThen { _ =>
      invalidate(player.name)
    }
  }

  override def delete(id: Int): Future[Boolean] = {
    underlying.findById(id).flatMap { playerOpt =>
      underlying.delete(id).andThen { _ =>
        playerOpt.foreach(p => invalidate(p.name))
      }
    }
  }

  override def findAll(): Future[List[Player]] = {
    underlying.findAll()
  }

  override def getOrCreateId(name: String): Future[Int] = {
    // Always check cache first, but writes are delegated to the underlying DAO.
    findByName(name).flatMap {
      case Some(player) =>
        underlying.getOrCreateId(player.name).andThen { _ =>
          invalidate(name)
        }
      case None =>
        underlying.getOrCreateId(name).andThen { _ =>
          invalidate(name)
        }
    }
  }

  override def updateElo(id: Int, newElo: Int): Future[Boolean] = {
    underlying.findById(id).flatMap { playerOpt =>
      underlying.updateElo(id, newElo).andThen { _ =>
        playerOpt.foreach(p => invalidate(p.name))
      }
    }
  }

  override def findLeaderboard(): Future[List[(Player, Int)]] = {
    underlying.findLeaderboard()
  }

  private def invalidate(name: String): Unit = {
    val jedis = pool.getResource
    try {
      jedis.del(cacheKey(name))
    } finally {
      jedis.close()
    }
  }

  private def withCache(name: String)(load: => Future[Option[Player]]): Future[Option[Player]] = {
    val jedis = pool.getResource
    try {
      val cached = Option(jedis.get(cacheKey(name)))
      cached match {
        case Some(json) =>
          Future.successful(Some(read[Player](json)))
        case None =>
          load.map { result =>
            result.foreach { player =>
              val jedis2 = pool.getResource
              try {
                jedis2.setex(cacheKey(name), ttlSeconds, write(player))
              } finally {
                jedis2.close()
              }
            }
            result
          }
      }
    } finally {
      jedis.close()
    }
  }
}
