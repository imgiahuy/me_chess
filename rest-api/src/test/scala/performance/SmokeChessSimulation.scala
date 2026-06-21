package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class SmokeChessSimulation extends BaseChessSimulation {

  setUp(
    createGameScenario.inject(atOnceUsers(1)),
    readGameScenario.inject(atOnceUsers(1)),
    playGameScenario.inject(atOnceUsers(1)),
    listGamesScenario.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.max.lt(2000),
      global.successfulRequests.percent.gt(95)
    )
}
