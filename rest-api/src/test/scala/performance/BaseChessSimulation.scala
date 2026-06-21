package performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

abstract class BaseChessSimulation extends Simulation {

  val baseUrl = sys.props.getOrElse("gatling.baseUrl", "http://localhost:8081/v1/chess")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .doNotTrackHeader("1")

  val defaultPause = 1 second

  val playerFeeder = csv("performance/players.csv").random
  val moveFeeder = csv("performance/openingMoves.csv").random

  val createGame = exec(
    http("Create Game")
      .post("/games")
      .body(StringBody("""{"whitePlayer":"${whitePlayer}","blackPlayer":"${blackPlayer}","timeControl":"${timeControl}"}"""))
      .asJson
      .check(status.is(201))
      .check(jsonPath("$.gameId").saveAs("gameId"))
  )

  val getGame = exec(
    http("Get Game")
      .get(session => s"/games/${session("gameId").as[String]}")
      .check(status.is(200))
  )

  val makeMove = exec(
    http("Make Move")
      .post(session => s"/games/${session("gameId").as[String]}/moves")
      .body(StringBody("""{"from":"${from}","to":"${to}"}"""))
      .asJson
      .check(status.in(200, 400))
  )

  val listGames = exec(
    http("List Games")
      .get("/games")
      .check(status.is(200))
  )

  val loadLatest = exec(
    http("Load Latest")
      .post("/games/load-latest")
      .check(status.in(201, 400, 404))
  )

  val getStatus = exec(
    http("Get Status")
      .get(session => s"/games/${session("gameId").as[String]}/status")
      .check(status.is(200))
  )

  val deleteGame = exec(
    http("Delete Game")
      .delete(session => s"/games/${session("gameId").as[String]}")
      .check(status.in(200, 404))
  )

  val createGameScenario = scenario("Create Game")
    .feed(playerFeeder)
    .exec(createGame)
    .pause(defaultPause)

  val readGameScenario = scenario("Read Game")
    .feed(playerFeeder)
    .exec(createGame)
    .pause(defaultPause)
    .exec(getGame)
    .pause(defaultPause)
    .exec(getStatus)

  val playGameScenario = scenario("Play Game")
    .feed(playerFeeder)
    .exec(createGame)
    .pause(500.milliseconds)
    .repeat(5) {
      feed(moveFeeder)
        .exec(makeMove)
        .pause(200.milliseconds)
    }
    .exec(getStatus)
    .pause(defaultPause)

  val listGamesScenario = scenario("List Games")
    .exec(listGames)
    .pause(defaultPause)

  val endToEndScenario = scenario("End-to-End Game")
    .feed(playerFeeder)
    .exec(createGame)
    .pause(500.milliseconds)
    .exec(getGame)
    .pause(500.milliseconds)
    .repeat(5) {
      feed(moveFeeder)
        .exec(makeMove)
        .pause(200.milliseconds)
    }
    .exec(getStatus)
    .exec(deleteGame)
}
