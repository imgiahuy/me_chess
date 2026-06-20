package integration

import database.DatabaseManager
import model.{Board, Ongoing, Player, Position, PositionState, White}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.testcontainers.containers.PostgreSQLContainer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/** Integration test for PostgreSQL persistence using Testcontainers. */
class PostgresPersistenceSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

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
    if (dbManager != null) {
      await(dbManager.close())
    }
    postgres.stop()
  }

  override def beforeEach(): Unit = {
    await(dbManager.resetSchema())
  }

  test("PostgreSQL: create and find a game by id") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))

    val found = await(dbManager.gameDao.findById(gameId))
    found shouldBe defined
    found.get.whitePlayer.name shouldBe "white"
    found.get.blackPlayer.name shouldBe "black"
  }

  test("PostgreSQL: list all games") {
    val game = newGame("alice", "bob")
    await(dbManager.gameDao.create(game))

    val games = await(dbManager.gameDao.findAll())
    games should not be empty
  }

  test("PostgreSQL: update a game") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))
    val updated = game.copy(id = Some(gameId), gameResult = Ongoing)

    await(dbManager.gameDao.update(gameId, updated)) shouldBe true
    val found = await(dbManager.gameDao.findById(gameId))
    found shouldBe defined
    found.get.id shouldBe Some(gameId)
  }

  test("PostgreSQL: delete a game") {
    val game = newGame("white", "black")
    val gameId = await(dbManager.gameDao.create(game))

    await(dbManager.gameDao.delete(gameId)) shouldBe true
    await(dbManager.gameDao.findById(gameId)) shouldBe None
  }

  test("PostgreSQL: findLatest returns a game when one exists") {
    val game = newGame("latest-white", "latest-black")
    await(dbManager.gameDao.create(game))

    val latest = await(dbManager.gameDao.findLatest())
    latest shouldBe defined
  }

  test("PostgreSQL: listSummaries returns real moveCount and isGameOver") {
    val game = newGame("s-white", "s-black")
    await(dbManager.gameDao.create(game))

    val summaries = await(dbManager.gameDao.listSummaries())
    summaries should not be empty
    val (_, _, moveCount, isGameOver) = summaries.head
    moveCount shouldBe 0
    isGameOver shouldBe false
  }

  test("PostgreSQL: updateElo persists the new rating") {
    val playerId = await(dbManager.playerDao.getOrCreateId("elo-player"))
    val updated = await(dbManager.playerDao.updateElo(playerId, 1400))
    updated shouldBe true
  }

  test("PostgreSQL: findLeaderboard returns players sorted by ELO desc") {
    await(dbManager.playerDao.getOrCreateId("lb-low"))
    val highId = await(dbManager.playerDao.getOrCreateId("lb-high"))
    await(dbManager.playerDao.updateElo(highId, 1600))

    val board = await(dbManager.playerDao.findLeaderboard())
    board should not be empty
    board.head._2 should be >= board.last._2
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
