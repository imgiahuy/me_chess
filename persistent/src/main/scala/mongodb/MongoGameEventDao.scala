package mongodb

import dao.{GameEvent, GameEventDao}
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.{Filters, Sorts, Updates}
import org.bson.Document
import com.mongodb.client.MongoClient

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/** MongoDB implementation of GameEventDao for the transactional outbox pattern.
  *
  * Events are stored in a `game_events` collection with an index on
  * `published` status for efficient polling of unpublished events.
  */
class MongoGameEventDao(client: MongoClient, databaseName: String)(implicit ec: ExecutionContext) extends GameEventDao {

  private val database = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("game_events")
  private val idCounter = new java.util.concurrent.atomic.AtomicLong(
    Option(collection.find().sort(new Document("eventId", -1)).first())
      .flatMap(doc => Option(doc.getLong("eventId")).map(_.toLong))
      .getOrElse(0L)
  )

  override def insert(event: GameEvent): Future[Long] = {
    Future {
      val newId = idCounter.incrementAndGet()
      val doc = new Document()
        .append("eventId", newId)
        .append("gameId", event.gameId)
        .append("eventType", event.eventType)
        .append("payload", event.payload)
        .append("createdAt", java.util.Date.from(event.createdAt))
        .append("published", event.published)
        .append("publishedAt", event.publishedAt.map(java.util.Date.from).orNull)
      collection.insertOne(doc)
      newId
    }
  }

  override def findUnpublished(limit: Int = 100): Future[List[GameEvent]] = {
    Future {
      collection.find(Filters.eq("published", false))
        .sort(Sorts.ascending("createdAt"))
        .limit(limit)
        .asScala
        .map(docToEvent)
        .toList
    }
  }

  override def markPublished(id: Long): Future[Boolean] = {
    Future {
      val result = collection.updateOne(
        Filters.eq("eventId", id),
        Updates.combine(
          Updates.set("published", true),
          Updates.set("publishedAt", new java.util.Date())
        )
      )
      result.getModifiedCount > 0
    }
  }

  override def markPublishedBatch(ids: Seq[Long]): Future[Int] = {
    Future {
      val result = collection.updateMany(
        Filters.in("eventId", ids.map(Long.box).asJava),
        Updates.combine(
          Updates.set("published", true),
          Updates.set("publishedAt", new java.util.Date())
        )
      )
      result.getModifiedCount.toInt
    }
  }

  override def findByGameId(gameId: String): Future[List[GameEvent]] = {
    Future {
      collection.find(Filters.eq("gameId", gameId))
        .sort(Sorts.ascending("createdAt"))
        .asScala
        .map(docToEvent)
        .toList
    }
  }

  override def deletePublishedBefore(before: java.time.Instant): Future[Int] = {
    Future {
      val result = collection.deleteMany(
        Filters.and(
          Filters.eq("published", true),
          Filters.lt("createdAt", java.util.Date.from(before))
        )
      )
      result.getDeletedCount.toInt
    }
  }

  private def docToEvent(doc: Document): GameEvent = {
    GameEvent(
      id = doc.getLong("eventId"),
      gameId = doc.getString("gameId"),
      eventType = doc.getString("eventType"),
      payload = doc.getString("payload"),
      createdAt = doc.getDate("createdAt").toInstant,
      published = doc.getBoolean("published", false),
      publishedAt = Option(doc.getDate("publishedAt")).map(_.toInstant)
    )
  }
}
