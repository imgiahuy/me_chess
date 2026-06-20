package redis

import dao.GameDao
import model.PositionState
import redis.clients.jedis.JedisPool
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

/** GameDao decorator that caches game lookups in Redis.
  *
  * Cache entries are stored as JSON with a configurable TTL. Write operations
  * invalidate the cached entry for that game ID so reads never return stale
  * data after an update or delete.
  */
class CachedGameDao(
  underlying: GameDao,
  pool: JedisPool,
  ttlSeconds: Int = 120
)(implicit ec: ExecutionContext) extends GameDao {

  private def cacheKey(id: String): String = s"game:id:$id"
  private def summariesKey: String = "games:summaries"
  private def allGamesKey: String = "games:all_ids"

  override def create(game: PositionState): Future[String] = {
    underlying.create(game).andThen { case scala.util.Success(gameId) =>
      invalidateSummaries()
    }
  }

  override def findById(id: String): Future[Option[PositionState]] = {
    // Game state is complex to serialize; we skip caching full PositionState
    // and instead cache at the summary/list level for performance.
    // For individual game lookups, delegate directly to underlying.
    underlying.findById(id)
  }

  override def update(id: String, game: PositionState): Future[Boolean] = {
    underlying.update(id, game).andThen { case scala.util.Success(true) =>
      invalidate(id)
      invalidateSummaries()
    }
  }

  override def delete(id: String): Future[Boolean] = {
    underlying.delete(id).andThen { case scala.util.Success(true) =>
      invalidate(id)
      invalidateSummaries()
    }
  }

  override def listAll(): Future[List[String]] = {
    withListCache(allGamesKey) {
      underlying.listAll()
    }
  }

  override def findAll(): Future[List[PositionState]] = {
    underlying.findAll()
  }

  override def listSummaries(): Future[List[(String, String, Int, Boolean)]] = {
    withSummariesCache {
      underlying.listSummaries()
    }
  }

  override def findLatest(): Future[Option[PositionState]] = {
    underlying.findLatest()
  }

  private def invalidate(id: String): Unit = {
    val jedis = pool.getResource
    try {
      jedis.del(cacheKey(id))
    } finally {
      jedis.close()
    }
  }

  private def invalidateSummaries(): Unit = {
    val jedis = pool.getResource
    try {
      jedis.del(summariesKey)
      jedis.del(allGamesKey)
    } finally {
      jedis.close()
    }
  }

  private def withListCache(key: String)(load: => Future[List[String]]): Future[List[String]] = {
    val jedis = pool.getResource
    try {
      val cached = Option(jedis.get(key))
      cached match {
        case Some(json) if json.nonEmpty =>
          Future.successful(read[List[String]](json))
        case _ =>
          load.map { result =>
            val jedis2 = pool.getResource
            try {
              jedis2.setex(key, ttlSeconds, write(result))
            } finally {
              jedis2.close()
            }
            result
          }
      }
    } finally {
      jedis.close()
    }
  }

  private def withSummariesCache(load: => Future[List[(String, String, Int, Boolean)]]): Future[List[(String, String, Int, Boolean)]] = {
    val jedis = pool.getResource
    try {
      val cached = Option(jedis.get(summariesKey))
      cached match {
        case Some(json) if json.nonEmpty =>
          Future.successful(read[List[(String, String, Int, Boolean)]](json))
        case _ =>
          load.map { result =>
            val jedis2 = pool.getResource
            try {
              jedis2.setex(summariesKey, ttlSeconds, write(result))
            } finally {
              jedis2.close()
            }
            result
          }
      }
    } finally {
      jedis.close()
    }
  }
}
