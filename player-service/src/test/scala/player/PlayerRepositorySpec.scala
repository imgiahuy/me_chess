package player

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PlayerRepositorySpec extends AnyFunSuite with Matchers {

  test("create returns a player with generated ID and initial values") {
    val repo = new PlayerRepository()
    val req = CreatePlayerRequest("Alice", Some("alice@example.com"), 1500)

    val player = repo.create(req)

    player.id should not be empty
    player.username shouldBe "Alice"
    player.email shouldBe Some("alice@example.com")
    player.rating shouldBe 1500
    player.gamesPlayed shouldBe 0
    player.wins shouldBe 0
    player.losses shouldBe 0
    player.draws shouldBe 0
    player.createdAt should not be empty
    player.lastSeenAt should not be empty
  }

  test("create defaults email to None and rating to 1200") {
    val repo = new PlayerRepository()
    val req = CreatePlayerRequest("Bob")

    val player = repo.create(req)

    player.email shouldBe None
    player.rating shouldBe 1200
  }

  test("winRate returns 0.0 for new player") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("NewPlayer"))

    player.winRate shouldBe 0.0
  }

  test("winRate calculates percentage correctly") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice"))
    
    // Manually update via recordGame to set stats
    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "win", 10))
    repo.recordGame(RecordGameRequest(player.id, "opponent-2", "win", 10))
    repo.recordGame(RecordGameRequest(player.id, "opponent-3", "loss", -5))

    val updated = repo.findById(player.id).get
    updated.winRate shouldBe (2.0 / 3.0 * 100)
  }

  test("findById returns None for non-existent player") {
    val repo = new PlayerRepository()
    repo.findById("non-existent") shouldBe None
  }

  test("findById returns player for existing ID") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice"))

    repo.findById(player.id) shouldBe Some(player)
  }

  test("findByUsername returns None for non-existent username") {
    val repo = new PlayerRepository()
    repo.findByUsername("non-existent") shouldBe None
  }

  test("findByUsername returns player for existing username") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice"))

    repo.findByUsername("Alice") shouldBe Some(player)
  }

  test("findAll returns empty list when no players") {
    val repo = new PlayerRepository()
    repo.findAll() shouldBe empty
  }

  test("findAll returns all players sorted by username") {
    val repo = new PlayerRepository()
    repo.create(CreatePlayerRequest("Charlie"))
    repo.create(CreatePlayerRequest("Alice"))
    repo.create(CreatePlayerRequest("Bob"))

    val players = repo.findAll()
    players.map(_.username) shouldBe List("Alice", "Bob", "Charlie")
  }

  test("update modifies email and rating") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", Some("old@example.com"), 1200))

    val updated = repo.update(player.id, UpdatePlayerRequest(Some("new@example.com"), Some(1400)))

    updated shouldBe defined
    updated.get.email shouldBe Some("new@example.com")
    updated.get.rating shouldBe 1400
    updated.get.lastSeenAt should not be player.lastSeenAt
  }

  test("update with None keeps existing values") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", Some("alice@example.com"), 1200))

    val updated = repo.update(player.id, UpdatePlayerRequest(None, None))

    updated shouldBe defined
    updated.get.email shouldBe Some("alice@example.com")
    updated.get.rating shouldBe 1200
  }

  test("update returns None for non-existent player") {
    val repo = new PlayerRepository()
    repo.update("non-existent", UpdatePlayerRequest()) shouldBe None
  }

  test("recordGame increments gamesPlayed and updates rating on win") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", initialRating = 1200))

    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "win", 10))

    val updated = repo.findById(player.id).get
    updated.gamesPlayed shouldBe 1
    updated.wins shouldBe 1
    updated.losses shouldBe 0
    updated.draws shouldBe 0
    updated.rating shouldBe 1210
  }

  test("recordGame increments losses on loss") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", initialRating = 1200))

    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "loss", -10))

    val updated = repo.findById(player.id).get
    updated.losses shouldBe 1
    updated.rating shouldBe 1190
  }

  test("recordGame increments draws on draw") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", initialRating = 1200))

    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "draw", 0))

    val updated = repo.findById(player.id).get
    updated.draws shouldBe 1
    updated.rating shouldBe 1200
  }

  test("recordGame with invalid result does not change stats") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", initialRating = 1200))

    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "invalid", 0))

    val updated = repo.findById(player.id).get
    updated.gamesPlayed shouldBe 0
    updated.wins shouldBe 0
    updated.losses shouldBe 0
    updated.draws shouldBe 0
    updated.rating shouldBe 1200
  }

  test("recordGame returns None for non-existent player") {
    val repo = new PlayerRepository()
    repo.recordGame(RecordGameRequest("non-existent", "opponent-1", "win", 10)) shouldBe None
  }

  test("delete removes player") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice"))

    repo.delete(player.id) shouldBe true
    repo.findById(player.id) shouldBe None
  }

  test("delete returns false for non-existent player") {
    val repo = new PlayerRepository()
    repo.delete("non-existent") shouldBe false
  }

  test("leaderboard returns empty list when no players") {
    val repo = new PlayerRepository()
    repo.leaderboard() shouldBe empty
  }

  test("leaderboard returns players sorted by rating descending") {
    val repo = new PlayerRepository()
    val p1 = repo.create(CreatePlayerRequest("Alice", initialRating = 1400))
    val p2 = repo.create(CreatePlayerRequest("Bob", initialRating = 1600))
    val p3 = repo.create(CreatePlayerRequest("Charlie", initialRating = 1500))

    val leaderboard = repo.leaderboard()
    leaderboard.map(_.username) shouldBe List("Bob", "Charlie", "Alice")
    leaderboard.map(_.rank) shouldBe List(1, 2, 3)
  }

  test("leaderboard respects limit parameter") {
    val repo = new PlayerRepository()
    repo.create(CreatePlayerRequest("Alice", initialRating = 1400))
    repo.create(CreatePlayerRequest("Bob", initialRating = 1600))
    repo.create(CreatePlayerRequest("Charlie", initialRating = 1500))

    val leaderboard = repo.leaderboard(limit = 2)
    leaderboard should have size 2
    leaderboard.map(_.username) shouldBe List("Bob", "Charlie")
  }

  test("leaderboard includes correct stats") {
    val repo = new PlayerRepository()
    val player = repo.create(CreatePlayerRequest("Alice", initialRating = 1200))
    repo.recordGame(RecordGameRequest(player.id, "opponent-1", "win", 10))
    repo.recordGame(RecordGameRequest(player.id, "opponent-2", "loss", -5))

    val leaderboard = repo.leaderboard()
    val entry = leaderboard.head
    entry.playerId shouldBe player.id
    entry.username shouldBe "Alice"
    entry.rating shouldBe 1205
    entry.gamesPlayed shouldBe 2
    entry.wins shouldBe 1
    entry.losses shouldBe 1
    entry.draws shouldBe 0
    entry.winRate shouldBe 50.0
    entry.rank shouldBe 1
  }
}
