package performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class ChessApiSimulation extends Simulation {

  // Base URL for the API
  val baseUrl = "http://localhost:8081/v1/chess"

  // HTTP configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Scenario 1: Create Game
  val createGameScenario = scenario("Create Game")
    .exec(
      http("Create New Game")
        .post("/games")
        .body(StringBody("""{"whitePlayer":"Player1","blackPlayer":"Player2"}"""))
        .check(status.is(201))
        .check(jsonPath("$.gameId").saveAs("gameId"))
    )
    .pause(1)

  // Scenario 2: Get Game
  val getGameScenario = scenario("Get Game")
    .exec(createGameScenario)
    .exec(
      http("Get Game State")
        .get("/games/${gameId}")
        .check(status.is(200))
    )
    .pause(1)

  // Scenario 3: Make Move
  val makeMoveScenario = scenario("Make Move")
    .exec(createGameScenario)
    .exec(
      http("Make Move")
        .post("/games/${gameId}/moves")
        .body(StringBody("""{"from":"e2","to":"e4"}"""))
        .check(status.is(200))
    )
    .pause(1)

  // Scenario 4: List Games
  val listGamesScenario = scenario("List Games")
    .exec(
      http("List All Games")
        .get("/games")
        .check(status.is(200))
    )
    .pause(1)

  // Scenario 5: Load Latest Game
  val loadLatestScenario = scenario("Load Latest Game")
    .exec(
      http("Load Latest Game")
        .post("/games/load-latest")
        .check(status.is(201))
    )
    .pause(1)

  // Scenario 6: Complete Game Flow
  val completeGameFlow = scenario("Complete Game Flow")
    .exec(
      http("Create Game")
        .post("/games")
        .body(StringBody("""{"whitePlayer":"White","blackPlayer":"Black"}"""))
        .check(status.is(201))
        .check(jsonPath("$.gameId").saveAs("gameId"))
    )
    .pause(500.milliseconds)
    .repeat(5) { // Make 5 moves
      exec(
        http("Make Move")
          .post("/games/${gameId}/moves")
          .body(StringBody("""{"from":"e2","to":"e4"}"""))
          .check(status.in(200, 400)) // 400 is acceptable for invalid moves
      )
      .pause(200.milliseconds)
    }
    .exec(
      http("Get Game Status")
        .get("/games/${gameId}/status")
        .check(status.is(200))
    )

  // Load test configuration
  setUp(
    createGameScenario.inject(
      rampUsersPerSec(1) to 10 during (30 seconds),
      constantUsersPerSec(10) during (30 seconds)
    ),
    getGameScenario.inject(
      rampUsersPerSec(1) to 15 during (30 seconds),
      constantUsersPerSec(15) during (30 seconds)
    ),
    makeMoveScenario.inject(
      rampUsersPerSec(1) to 8 during (30 seconds),
      constantUsersPerSec(8) during (30 seconds)
    ),
    listGamesScenario.inject(
      rampUsersPerSec(1) to 20 during (30 seconds),
      constantUsersPerSec(20) during (30 seconds)
    ),
    loadLatestScenario.inject(
      rampUsersPerSec(1) to 5 during (30 seconds),
      constantUsersPerSec(5) during (30 seconds)
    ),
    completeGameFlow.inject(
      rampUsersPerSec(1) to 5 during (30 seconds),
      constantUsersPerSec(5) during (30 seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(500), // 95th percentile response time < 500ms
      global.successfulRequests.percent.gte(95) // 95% success rate
    )
}
