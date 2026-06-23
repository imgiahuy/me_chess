package inmemory

import dao.{GameEvent, GameEventDao}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicLong

/** In-memory implementation of GameEventDao for testing and lightweight deployments. */
class InMemoryGameEventDao(implicit ec: ExecutionContext) extends GameEventDao {

  private val events: mutable.ListBuffer[GameEvent] = mutable.ListBuffer.empty
  private val nextId: AtomicLong = new AtomicLong(1)

  override def insert(event: GameEvent): Future[Long] = Future {
    events.synchronized {
      val id = nextId.getAndIncrement()
      events += event.copy(id = id)
      id
    }
  }

  override def findUnpublished(limit: Int = 100): Future[List[GameEvent]] = Future {
    events.synchronized {
      events.filter(!_.published).sortBy(_.createdAt).take(limit).toList
    }
  }

  override def markPublished(id: Long): Future[Boolean] = Future {
    events.synchronized {
      val idx = events.indexWhere(_.id == id)
      if (idx >= 0) {
        events(idx) = events(idx).copy(published = true, publishedAt = Some(java.time.Instant.now()))
        true
      } else false
    }
  }

  override def markPublishedBatch(ids: Seq[Long]): Future[Int] = Future {
    events.synchronized {
      val idSet = ids.toSet
      var count = 0
      val now = java.time.Instant.now()
      events.indices.foreach { idx =>
        if (idSet.contains(events(idx).id) && !events(idx).published) {
          events(idx) = events(idx).copy(published = true, publishedAt = Some(now))
          count += 1
        }
      }
      count
    }
  }

  override def findByGameId(gameId: String): Future[List[GameEvent]] = Future {
    events.synchronized {
      events.filter(_.gameId == gameId).sortBy(_.createdAt).toList
    }
  }

  override def deletePublishedBefore(before: java.time.Instant): Future[Int] = Future {
    events.synchronized {
      val toRemove = events.filter(e => e.published && e.createdAt.isBefore(before))
      val count = toRemove.size
      events --= toRemove
      count
    }
  }

  /** Clears all stored events. Useful between tests. */
  def clear(): Unit = events.synchronized {
    events.clear()
  }

  /** Returns all events for inspection in tests. */
  def allEvents: List[GameEvent] = events.synchronized(events.toList)
}
