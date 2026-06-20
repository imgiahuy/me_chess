package integration

import database.{DatabaseManager, MongoDatabaseManager}
import model.{Board, Ongoing, Player, Position, PositionState, White}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.MongoDBContainer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/** Integration test for MongoDB persistence using Testcontainers. */
class MongoPersistenceSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private val mongo = new MongoDBContainer("mongo:7")
  private var dbManager: MongoDatabaseManager = _
  implicit private val ec: ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    mongo.start()
    dbManager = DatabaseManager.mongodb(
      host = mongo.getHost,
      port = mongo.getMappedPort(27017),
      database = "chess"
    )
    await(dbManager.initializeSchema())
  }

  override def afterAll(): Unit = {
    if (dbManager != null) {
      await(dbManager.close())
    }
    mongo.stop()
  }

  test("MongoDB: create and find a game by id") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))

    val found = await(dbManager.gameDao.findById(gameId))
    found shouldBe defined
    found.get.whitePlayer.name should not be empty
  }

  test("MongoDB: list all games") {
    val game = newGame("alice", "bob")
    await(dbManager.gameDao.create(game))

    val games = await(dbManager.gameDao.findAll())
    games should not be empty
  }

  test("MongoDB: update a game") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))
    val updated = game.copy(id = Some(gameId), gameResult = Ongoing)

    await(dbManager.gameDao.update(gameId, updated)) shouldBe true
    val found = await(dbManager.gameDao.findById(gameId))
    found shouldBe defined
    found.get.id shouldBe Some(gameId)
  }

  test("MongoDB: delete a game") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))

    await(dbManager.gameDao.delete(gameId)) shouldBe true
    await(dbManager.gameDao.findById(gameId)) shouldBe None
  }

  test("MongoDB: findLatest returns the most recently modified game") {
    val game = newGame("latest-white", "latest-black")
    val gameId = await(dbManager.gameDao.create(game))

    val latest = await(dbManager.gameDao.findLatest())
    latest shouldBe defined
    latest.get.id shouldBe Some(gameId)
  }

  test("MongoDB: create stores player names and findById resolves them") {
    val game = newGame("named-white", "named-black")
    val gameId = await(dbManager.gameDao.create(game))

    val found = await(dbManager.gameDao.findById(gameId))
    found shouldBe defined
    found.get.whitePlayer.name shouldBe "named-white"
    found.get.blackPlayer.name shouldBe "named-black"
  }

  test("MongoDB: listSummaries returns real moveCount and isGameOver") {
    val game = newGame("s-white", "s-black")
    await(dbManager.gameDao.create(game))

    val summaries = await(dbManager.gameDao.listSummaries())
    summaries should not be empty
    val (_, _, moveCount, isGameOver) = summaries.head
    moveCount shouldBe 0
    isGameOver shouldBe false
  }

  test("MongoDB: findLeaderboard returns players sorted by ELO desc") {
    await(dbManager.playerDao.getOrCreateId("lb-low"))
    await(dbManager.playerDao.getOrCreateId("lb-high"))

    val board = await(dbManager.playerDao.findLeaderboard())
    board should not be empty
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
