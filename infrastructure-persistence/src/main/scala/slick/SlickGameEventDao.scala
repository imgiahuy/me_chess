package slick

import dao.{GameEvent, GameEventDao}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Slick implementation of GameEventDao for the transactional outbox pattern. */
class SlickGameEventDao(db: Database, tables: Tables)(implicit ec: ExecutionContext) extends GameEventDao {
  import tables.profile.api._

  // Table definition for game_events
  private class GameEvents(tag: Tag) extends Table[(Long, String, String, String, java.sql.Timestamp, Boolean, Option[java.sql.Timestamp])](tag, "game_events") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def gameId = column[String]("game_id")
    def eventType = column[String]("event_type")
    def payload = column[String]("payload")
    def createdAt = column[java.sql.Timestamp]("created_at")
    def published = column[Boolean]("published")
    def publishedAt = column[Option[java.sql.Timestamp]]("published_at")

    def * = (id, gameId, eventType, payload, createdAt, published, publishedAt)
  }

  private val gameEvents = TableQuery[GameEvents]

  override def insert(event: GameEvent): Future[Long] = {
    val action = (gameEvents returning gameEvents.map(_.id)) +=
      (0L, event.gameId, event.eventType, event.payload,
        java.sql.Timestamp.from(event.createdAt), event.published, event.publishedAt.map(java.sql.Timestamp.from))
    db.run(action)
  }

  override def findUnpublished(limit: Int = 100): Future[List[GameEvent]] = {
    val action = gameEvents
      .filter(_.published === false)
      .sortBy(_.createdAt.asc)
      .take(limit)
      .result
    db.run(action).map(_.map(rowToEvent).toList)
  }

  override def markPublished(id: Long): Future[Boolean] = {
    val now = java.sql.Timestamp.from(java.time.Instant.now())
    val action = gameEvents
      .filter(_.id === id)
      .map(e => (e.published, e.publishedAt))
      .update((true, Some(now)))
    db.run(action).map(_ > 0)
  }

  override def markPublishedBatch(ids: Seq[Long]): Future[Int] = {
    val now = java.sql.Timestamp.from(java.time.Instant.now())
    val action = gameEvents
      .filter(_.id inSet ids)
      .map(e => (e.published, e.publishedAt))
      .update((true, Some(now)))
    db.run(action)
  }

  override def findByGameId(gameId: String): Future[List[GameEvent]] = {
    val action = gameEvents
      .filter(_.gameId === gameId)
      .sortBy(_.createdAt.asc)
      .result
    db.run(action).map(_.map(rowToEvent).toList)
  }

  override def deletePublishedBefore(before: java.time.Instant): Future[Int] = {
    val ts = java.sql.Timestamp.from(before)
    val action = gameEvents
      .filter(e => e.published === true && e.createdAt < ts)
      .delete
    db.run(action)
  }

  private def rowToEvent(row: (Long, String, String, String, java.sql.Timestamp, Boolean, Option[java.sql.Timestamp])): GameEvent = {
    val (id, gameId, eventType, payload, createdAt, published, publishedAt) = row
    GameEvent(
      id = id,
      gameId = gameId,
      eventType = eventType,
      payload = payload,
      createdAt = createdAt.toInstant,
      published = published,
      publishedAt = publishedAt.map(_.toInstant)
    )
  }
}
