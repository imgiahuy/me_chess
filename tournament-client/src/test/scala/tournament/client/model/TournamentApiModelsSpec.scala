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
  
  "RegisterResponse" should "create with correct fields" in {
    val response = RegisterResponse(id = "bot-123", token = "jwt-token")
    
    response.id shouldBe "bot-123"
    response.token shouldBe "jwt-token"
  }
  
  "TournamentListResponse" should "group tournaments by status" in {
    val created = List(
      TournamentSummary("id1", "Tournament1", "created", "roundrobin", 3, 0, 2, Instant.now())
    )
    val started = List(
      TournamentSummary("id2", "Tournament2", "started", "swiss", 5, 2, 4, Instant.now())
    )
    val finished = List(
      TournamentSummary("id3", "Tournament3", "finished", "arena", 10, 10, 8, Instant.now())
    )
    
    val response = TournamentListResponse(created, started, finished)
    
    response.created should have length 1
    response.started should have length 1
    response.finished should have length 1
  }
  
  "GameStartEvent" should "create with correct fields" in {
    val event = GameStartEvent(
      tournamentId = "t1",
      round = 1,
      gameId = "g1",
      color = "white",
      opponentId = "opp1",
      opponentName = "OpponentBot",
      timestamp = Instant.now()
    )
    
    event.`type` shouldBe "gameStart"
    event.tournamentId shouldBe "t1"
    event.color shouldBe "white"
  }
  
  "GameStateEvent" should "create with correct fields" in {
    val event = GameStateEvent(
      tournamentId = "t1",
      gameId = "g1",
      fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      turn = "white",
      whiteTimeMs = 300000,
      blackTimeMs = 300000,
      timestamp = Instant.now()
    )
    
    event.`type` shouldBe "gameState"
    event.fen should startWith("rnbqkbnr")
    event.turn shouldBe "white"
  }
  
  "HeartbeatEvent" should "be identifiable as heartbeat" in {
    val event = HeartbeatEvent(timestamp = Instant.now())
    
    event.`type` shouldBe "heartbeat"
  }
  
  "RegisterBotRequest" should "create with correct fields" in {
    val request = RegisterBotRequest(
      name = "StockfishBot",
      family = "heuristic",
      strategyType = "greedy",
      engineType = "internal",
      modelVersion = "1"
    )
    
    request.name shouldBe "StockfishBot"
    request.family shouldBe "heuristic"
    request.strategyType shouldBe "greedy"
  }
}
