package integration

import dao.GameEvent
import database.DatabaseManager
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.testcontainers.containers.PostgreSQLContainer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/** Integration test for the GameEventDao outbox pattern using Testcontainers PostgreSQL. */
class GameEventOutboxSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private val postgres = new PostgreSQLContainer("postgres:15")
  private var dbManager: DatabaseManager = _
  implicit private val ec: ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    postgres.start()
    dbManager = DatabaseManager.postgresqlFromUrl(
      jdbcUrl = postgres.getJdbcUrl,
      user = postgres.getUsername,
      password = postgres.getPassword
    )
    await(
      dbManager.initializeSchemaWithMigrations(
        postgres.getJdbcUrl,
        postgres.getUsername,
        postgres.getPassword
      )
    )
  }

  override def afterAll(): Unit = {
    if (dbManager != null) await(dbManager.close())
    postgres.stop()
  }

  override def beforeEach(): Unit = {
    await(dbManager.resetSchema())
  }

  test("insert and findUnpublished returns the event") {
    val eventDao = dbManager.gameEventDao

    // Need a game to satisfy FK constraint - create a player and game first
    val playerId = await(dbManager.playerDao.getOrCreateId("outbox-player"))
    val game = model.PositionState(
      board = model.Board.empty,
      turn = model.White,
      moveHistory = List.empty,
      whitePlayer = model.Player("outbox-player"),
      blackPlayer = model.Player("outbox-player2")
    )
    val gameId = await(dbManager.gameDao.create(game))

    val event = GameEvent(
      gameId = gameId,
      eventType = "game_created",
      payload = """{"gameId":"test","white":"outbox-player","black":"outbox-player2"}"""
    )

    val id = await(eventDao.insert(event))
    id should be > 0L

    val unpublished = await(eventDao.findUnpublished())
    unpublished should not be empty
    unpublished.head.gameId shouldBe gameId
    unpublished.head.eventType shouldBe "game_created"
    unpublished.head.published shouldBe false
  }

  test("markPublished sets published to true") {
    val eventDao = dbManager.gameEventDao

    val playerId = await(dbManager.playerDao.getOrCreateId("pub-player"))
    val game = model.PositionState(
      board = model.Board.empty,
      turn = model.White,
      moveHistory = List.empty,
      whitePlayer = model.Player("pub-player"),
      blackPlayer = model.Player("pub-player2")
    )
    val gameId = await(dbManager.gameDao.create(game))

    val event = GameEvent(
      gameId = gameId,
      eventType = "move_made",
      payload = """{"move":"e2e4"}"""
    )
    val id = await(eventDao.insert(event))

    await(eventDao.markPublished(id)) shouldBe true

    val unpublished = await(eventDao.findUnpublished())
    unpublished.filter(_.id == id) shouldBe empty
  }

  test("markPublishedBatch marks multiple events") {
    val eventDao = dbManager.gameEventDao

    val game = model.PositionState(
      board = model.Board.empty,
      turn = model.White,
      moveHistory = List.empty,
      whitePlayer = model.Player("batch-w"),
      blackPlayer = model.Player("batch-b")
    )
    val gameId = await(dbManager.gameDao.create(game))

    val id1 = await(eventDao.insert(GameEvent(gameId = gameId, eventType = "move_made", payload = """{"move":"e2e4"}""")))
    val id2 = await(eventDao.insert(GameEvent(gameId = gameId, eventType = "move_made", payload = """{"move":"e7e5"}""")))
    val id3 = await(eventDao.insert(GameEvent(gameId = gameId, eventType = "move_made", payload = """{"move":"d2d4"}""")))

    val count = await(eventDao.markPublishedBatch(Seq(id1, id2)))
    count shouldBe 2

    val remaining = await(eventDao.findUnpublished())
    remaining.map(_.id) should contain only id3
  }

  test("findByGameId returns events for a specific game") {
    val eventDao = dbManager.gameEventDao

    val game1 = model.PositionState(
      board = model.Board.empty, turn = model.White, moveHistory = List.empty,
      whitePlayer = model.Player("g1-w"), blackPlayer = model.Player("g1-b")
    )
    val game2 = model.PositionState(
      board = model.Board.empty, turn = model.White, moveHistory = List.empty,
      whitePlayer = model.Player("g2-w"), blackPlayer = model.Player("g2-b")
    )
    val gameId1 = await(dbManager.gameDao.create(game1))
    val gameId2 = await(dbManager.gameDao.create(game2))

    await(eventDao.insert(GameEvent(gameId = gameId1, eventType = "game_created", payload = "{}")))
    await(eventDao.insert(GameEvent(gameId = gameId2, eventType = "game_created", payload = "{}")))
    await(eventDao.insert(GameEvent(gameId = gameId1, eventType = "move_made", payload = "{}")))

    val game1Events = await(eventDao.findByGameId(gameId1))
    game1Events.size shouldBe 2

    val game2Events = await(eventDao.findByGameId(gameId2))
    game2Events.size shouldBe 1
  }

  test("deletePublishedBefore removes old published events") {
    val eventDao = dbManager.gameEventDao

    val game = model.PositionState(
      board = model.Board.empty, turn = model.White, moveHistory = List.empty,
      whitePlayer = model.Player("del-w"), blackPlayer = model.Player("del-b")
    )
    val gameId = await(dbManager.gameDao.create(game))

    val id = await(eventDao.insert(GameEvent(gameId = gameId, eventType = "game_created", payload = "{}")))
    await(eventDao.markPublished(id))

    // Delete events published before now + 1 second
    val deleted = await(eventDao.deletePublishedBefore(java.time.Instant.now().plusSeconds(1)))
    deleted shouldBe 1

    val remaining = await(eventDao.findByGameId(gameId))
    remaining shouldBe empty
  }

  private def await[T](future: Future[T]): T = {
    Await.result(future, Duration(10, "seconds"))
  }
}
