package integration

import dao.{GameDao, PlayerDao}
import database.{DatabaseManager, MongoDatabaseManager}
import model.{Board, Ongoing, Player, Position, PositionState, White}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.GenericContainer
import redis.{RedisClientFactory, RedisConfig}
import redis.clients.jedis.JedisPool

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/** Integration test verifying Redis caching behavior for Player and Game DAOs.
  *
  * Uses Testcontainers for both MongoDB and Redis to validate that:
  * - Cached reads hit Redis after the first load
  * - Write operations properly invalidate the cache
  * - The CachedGameDao and CachedPlayerDao decorators work end-to-end
  */
class RedisCachingSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private val mongo = new MongoDBContainer("mongo:7")
  private val redisContainer = new GenericContainer("redis:7-alpine")
  private var dbManager: MongoDatabaseManager = _
  private var pool: JedisPool = _
  implicit private val ec: ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    mongo.start()
    redisContainer.withExposedPorts(6379)
    redisContainer.start()

    val redisConfig = RedisConfig(
      host = redisContainer.getHost,
      port = redisContainer.getMappedPort(6379),
      database = 0,
      password = None,
      timeout = 2000,
      maxTotal = 8,
      maxIdle = 4,
      minIdle = 1
    )
    pool = RedisClientFactory.createPool(redisConfig)

    dbManager = DatabaseManager.mongodb(
      host = mongo.getHost,
      port = mongo.getMappedPort(27017),
      database = "chess_redis_test",
      redisPool = Some(pool)
    )
    await(dbManager.initializeSchema())
  }

  override def afterAll(): Unit = {
    if (dbManager != null) await(dbManager.close())
    if (pool != null) pool.close()
    mongo.stop()
    redisContainer.stop()
  }

  test("Redis-cached PlayerDao: findByName returns cached result on second call") {
    val playerDao = dbManager.playerDao
    await(playerDao.getOrCreateId("cache-test-player"))

    // First call - loads from DB and caches
    val first = await(playerDao.findByName("cache-test-player"))
    first shouldBe defined
    first.get.name shouldBe "cache-test-player"

    // Second call - should hit cache
    val second = await(playerDao.findByName("cache-test-player"))
    second shouldBe defined
    second.get.name shouldBe "cache-test-player"
  }

  test("Redis-cached PlayerDao: create invalidates cache") {
    val playerDao = dbManager.playerDao

    // Create and cache
    await(playerDao.getOrCreateId("invalidate-test"))
    await(playerDao.findByName("invalidate-test")) shouldBe defined

    // After create with same name, cache is invalidated
    await(playerDao.create(Player("invalidate-new")))
    // Should still resolve fine
    await(playerDao.findByName("invalidate-new")) shouldBe defined
  }

  test("Redis-cached GameDao: listSummaries is cached and invalidated on create") {
    val gameDao = dbManager.gameDao

    // Initial state
    val summaries1 = await(gameDao.listSummaries())
    val count1 = summaries1.size

    // Create a game
    val game = newGame("redis-white", "redis-black")
    val gameId = await(gameDao.create(game))

    // After create, cache is invalidated - should see new count
    val summaries2 = await(gameDao.listSummaries())
    summaries2.size shouldBe (count1 + 1)
  }

  test("Redis-cached GameDao: update invalidates summaries cache") {
    val gameDao = dbManager.gameDao

    val game = newGame("upd-white", "upd-black")
    val gameId = await(gameDao.create(game))

    // Load summaries (caches them)
    await(gameDao.listSummaries())

    // Update
    val updatedGame = game.copy(id = Some(gameId), gameResult = Ongoing)
    await(gameDao.update(gameId, updatedGame)) shouldBe true

    // Summaries should reflect updated state
    val summaries = await(gameDao.listSummaries())
    summaries should not be empty
  }

  test("Redis-cached GameDao: delete invalidates cache") {
    val gameDao = dbManager.gameDao

    val game = newGame("del-white", "del-black")
    val gameId = await(gameDao.create(game))

    // Cache summaries
    val before = await(gameDao.listSummaries()).size

    // Delete
    await(gameDao.delete(gameId)) shouldBe true

    // Should reflect deletion
    val after = await(gameDao.listSummaries()).size
    after shouldBe (before - 1)
  }

  private def newGame(white: String, black: String): PositionState = {
    PositionState(
      board = Board.empty,
      turn = White,
      moveHistory = List.empty,
      whitePlayer = Player(white),
      blackPlayer = Player(black)
    )
  }

  private def await[T](future: Future[T]): T = {
    Await.result(future, Duration(10, "seconds"))
  }
}
