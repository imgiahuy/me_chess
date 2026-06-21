package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class CapacitySimulation extends BaseChessSimulation {

  setUp(
    createGameScenario.inject(
      rampUsersPerSec(1) to 100 during (10 minutes)
    ),
    readGameScenario.inject(
      rampUsersPerSec(1) to 200 during (10 minutes)
    ),
    playGameScenario.inject(
      rampUsersPerSec(1) to 80 during (10 minutes)
    ),
    listGamesScenario.inject(
      rampUsersPerSec(1) to 300 during (10 minutes)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(1500),
      global.responseTime.percentile4.lte(3000),
      global.successfulRequests.percent.gte(90),
      global.failedRequests.percent.lte(10)
    )
}
