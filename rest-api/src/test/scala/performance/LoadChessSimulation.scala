package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class LoadChessSimulation extends BaseChessSimulation {

  setUp(
    createGameScenario.inject(
      rampUsersPerSec(1) to 5 during (60 seconds)
    ),
    readGameScenario.inject(
      rampUsersPerSec(1) to 10 during (60 seconds)
    ),
    playGameScenario.inject(
      rampUsersPerSec(1) to 4 during (60 seconds)
    ),
    listGamesScenario.inject(
      rampUsersPerSec(2) to 15 during (60 seconds)
    ),
    endToEndScenario.inject(
      rampUsersPerSec(1) to 3 during (60 seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(800),
      global.responseTime.percentile4.lte(2000),
      global.successfulRequests.percent.gte(95),
      global.failedRequests.percent.lte(5)
    )
}
