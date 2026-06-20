package dao

import scala.concurrent.Future

/** Represents a domain event stored in the outbox table for reliable Kafka publishing. */
case class GameEvent(
  id: Long = 0L,
  gameId: String,
  eventType: String,
  payload: String,
  createdAt: java.time.Instant = java.time.Instant.now(),
  published: Boolean = false,
  publishedAt: Option[java.time.Instant] = None
)

/** Data Access Object interface for the game events outbox.
  *
  * Implements the transactional outbox pattern: events are persisted
  * alongside the game state in the same transaction, then a poller
  * publishes them to Kafka and marks them as sent.
  */
trait GameEventDao {

  /** Inserts a new event into the outbox. */
  def insert(event: GameEvent): Future[Long]

  /** Retrieves unpublished events ordered by creation time (oldest first).
    * @param limit maximum number of events to retrieve
    */
  def findUnpublished(limit: Int = 100): Future[List[GameEvent]]

  /** Marks an event as published. */
  def markPublished(id: Long): Future[Boolean]

  /** Marks multiple events as published in a batch. */
  def markPublishedBatch(ids: Seq[Long]): Future[Int]

  /** Retrieves all events for a given game (for event replay/sourcing). */
  def findByGameId(gameId: String): Future[List[GameEvent]]

  /** Deletes published events older than the given threshold (cleanup). */
  def deletePublishedBefore(before: java.time.Instant): Future[Int]
}
