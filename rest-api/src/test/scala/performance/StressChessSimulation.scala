package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class StressChessSimulation extends BaseChessSimulation {

  setUp(
    createGameScenario.inject(
      rampUsersPerSec(1) to 20 during (120 seconds)
    ),
    readGameScenario.inject(
      rampUsersPerSec(1) to 40 during (120 seconds)
    ),
    playGameScenario.inject(
      rampUsersPerSec(1) to 15 during (120 seconds)
    ),
    listGamesScenario.inject(
      rampUsersPerSec(5) to 50 during (120 seconds)
    ),
    endToEndScenario.inject(
      rampUsersPerSec(1) to 10 during (120 seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(1500),
      global.responseTime.percentile4.lte(3000),
      global.successfulRequests.percent.gte(90)
    )
}
