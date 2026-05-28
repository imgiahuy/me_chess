package performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class ChessApiSimulation extends Simulation {

  // =========================
  // CONFIG
  // =========================
  val baseUrl = "http://localhost:8081/v1/chess"

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // =========================
  // REUSABLE ACTIONS
  // =========================

  val createGame =
    exec(
      http("Create Game")
        .post("/games")
        .body(StringBody("""{"whitePlayer":"Player_ABCDEFGHIJKLMNOPQRSTUVWXYZ_123456789","blackPlayer":"Player_ABCDEFGHIJKLMNOPQRSTUVWXYZ_123456789"}"""))
        .asJson
        .check(status.is(201))
        .check(jsonPath("$.gameId").saveAs("gameId"))
    )

  val getGame =
    exec(
      http("Get Game")
        .get(session => s"/games/${session("gameId").as[String]}")
        .check(status.is(200))
    )

  val makeMove =
    exec(
      http("Make Move")
        .post(session => s"/games/${session("gameId").as[String]}/moves")
        .body(StringBody("""{"from":"e2","to":"e4"}"""))
        .asJson
        .check(status.in(200, 400))
    )

  val listGames =
    exec(
      http("List Games")
        .get("/games")
        .check(status.is(200))
    )

  val loadLatest =
    exec(
      http("Load Latest")
        .post("/games/load-latest")
        .check(status.is(201))
    )

  val getStatus =
    exec(
      http("Get Status")
        .get(session => s"/games/${session("gameId").as[String]}/status")
        .check(status.is(200))
    )

  // =========================
  // SCENARIOS (CLEAN)
  // =========================

  val createGameScenario =
    scenario("Create Game Flow")
      .exec(createGame)
      .pause(1)

  val getGameScenario =
    scenario("Get Game Flow")
      .exec(createGame)
      .exec(getGame)
      .pause(1)

  val makeMoveScenario =
    scenario("Make Move Flow")
      .exec(createGame)
      .repeat(5) {
        exec(makeMove).pause(200.milliseconds)
      }
      .exec(getStatus)

  val listGamesScenario =
    scenario("List Games Flow")
      .exec(listGames)
      .pause(1)

  val loadLatestScenario =
    scenario("Load Latest Flow")
      .exec(loadLatest)
      .pause(1)

  val completeGameFlow =
    scenario("Complete Game Flow")
      .exec(createGame)
      .pause(500.milliseconds)
      .repeat(5) {
        exec(makeMove).pause(200.milliseconds)
      }
      .exec(getStatus)

  // =========================
  // LOAD MODEL (REALISTIC)
  // =========================

  setUp(

    // WRITE OPERATIONS (5x LOAD FOR SCALABILITY TEST)
    createGameScenario.inject(
      rampUsersPerSec(1) to 5 during (60 seconds)
    ),

    makeMoveScenario.inject(
      rampUsersPerSec(1) to 4 during (60 seconds)
    ),

    loadLatestScenario.inject(
      rampUsersPerSec(1) to 3 during (60 seconds)
    ),

    completeGameFlow.inject(
      rampUsersPerSec(1) to 3 during (60 seconds)
    ),

    // READ OPERATIONS (5x LOAD FOR SCALABILITY TEST)
    getGameScenario.inject(
      rampUsersPerSec(2) to 10 during (60 seconds)
    ),

    listGamesScenario.inject(
      rampUsersPerSec(2) to 15 during (60 seconds)
    )

  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(800),
      global.successfulRequests.percent.gte(90)
    )
}