package tournament.client.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class TournamentApiModelsSpec extends AnyFlatSpec with Matchers {
  
  "RegisterRequest" should "create with correct fields" in {
    val request = RegisterRequest(name = "TestBot", isBot = true)
    
    request.name shouldBe "TestBot"
    request.isBot shouldBe true
  }
  
  "RegisterRequest" should "have default isBot = false" in {
    val request = RegisterRequest(name = "TestBot")
    
    request.name shouldBe "TestBot"
    request.isBot shouldBe false
  }
  
  "RegisterResponse" should "create with correct fields" in {
    val response = RegisterResponse(id = "bot-123", token = "jwt-token")
    
    response.id shouldBe "bot-123"
    response.token shouldBe "jwt-token"
  }
  
  "TournamentListResponse" should "group tournaments by status" in {
    val created = List(
      TournamentInfo(
        id = "id1",
        fullName = "Tournament1",
        clock = Clock(300, 3),
        variant = Variant("standard", "Standard"),
        rated = true,
        nbPlayers = 2,
        nbRounds = 3,
        format = "swiss",
        matchesPerPairing = 1,
        startPosition = "standard",
        createdBy = "user1",
        startsAt = "2024-01-01T00:00:00Z"
      )
    )
    val started = List(
      TournamentInfo(
        id = "id2",
        fullName = "Tournament2",
        clock = Clock(600, 5),
        variant = Variant("standard", "Standard"),
        rated = true,
        nbPlayers = 4,
        nbRounds = 5,
        format = "swiss",
        matchesPerPairing = 1,
        startPosition = "standard",
        createdBy = "user1",
        startsAt = "2024-01-01T00:00:00Z"
      )
    )
    val finished = List(
      TournamentInfo(
        id = "id3",
        fullName = "Tournament3",
        clock = Clock(300, 3),
        variant = Variant("standard", "Standard"),
        rated = true,
        nbPlayers = 8,
        nbRounds = 10,
        format = "arena",
        matchesPerPairing = 1,
        startPosition = "standard",
        createdBy = "user1",
        startsAt = "2024-01-01T00:00:00Z"
      )
    )
    
    val response = TournamentListResponse(created, started, finished)
    
    response.created should have length 1
    response.started should have length 1
    response.finished should have length 1
  }
  
  "TournamentEvent" should "create with correct fields" in {
    val event = TournamentEvent(
      `type` = "gameStart",
      round = Some(1),
      gameId = Some("g1"),
      color = Some("white"),
      winner = None
    )
    
    event.`type` shouldBe "gameStart"
    event.round shouldBe Some(1)
    event.gameId shouldBe Some("g1")
    event.color shouldBe Some("white")
  }
  
  "GameEvent" should "create with correct fields" in {
    val event = GameEvent(
      `type` = "gameState",
      uci = None,
      fen = Some("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
      turn = Some("white"),
      clock = Some(GameClock(300.0, 300.0, 3)),
      winner = None,
      status = None
    )
    
    event.`type` shouldBe "gameState"
    event.fen.map(_.startsWith("rnbqkbnr")) shouldBe Some(true)
    event.turn shouldBe Some("white")
  }
  
  "GameEvent" should "handle move event" in {
    val event = GameEvent(
      `type` = "move",
      uci = Some("e2e4"),
      fen = Some("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
      turn = Some("black"),
      clock = Some(GameClock(299.5, 300.0, 3)),
      winner = None,
      status = None
    )
    
    event.`type` shouldBe "move"
    event.uci shouldBe Some("e2e4")
  }
  
  "GameEvent" should "handle gameEnd event" in {
    val event = GameEvent(
      `type` = "gameEnd",
      uci = None,
      fen = None,
      turn = None,
      clock = None,
      winner = Some("white"),
      status = Some("checkmate")
    )
    
    event.`type` shouldBe "gameEnd"
    event.winner shouldBe Some("white")
    event.status shouldBe Some("checkmate")
  }
  
  "RegisterBotRequest" should "create with correct fields" in {
    val request = RegisterBotRequest(
      name = "StockfishBot",
      endpoint = Some("https://bots.example.com/stockfish"),
      family = Some("stockfish"),
      strategyType = Some("material-greedy"),
      engineType = Some("uci"),
      modelVersion = Some("16")
    )
    
    request.name shouldBe "StockfishBot"
    request.family shouldBe Some("stockfish")
    request.strategyType shouldBe Some("material-greedy")
  }
  
  "RegisterBotRequest" should "create with optional fields" in {
    val request = RegisterBotRequest(name = "SimpleBot")
    
    request.name shouldBe "SimpleBot"
    request.endpoint shouldBe None
    request.family shouldBe None
  }
  
  "BotRef" should "create with correct fields" in {
    val botRef = BotRef(id = "bot-123", name = "StockfishBot")
    
    botRef.id shouldBe "bot-123"
    botRef.name shouldBe "StockfishBot"
  }
}
