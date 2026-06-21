package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class SoakChessSimulation extends BaseChessSimulation {

  setUp(
    createGameScenario.inject(
      constantUsersPerSec(1) during (5 minutes)
    ),
    readGameScenario.inject(
      constantUsersPerSec(2) during (5 minutes)
    ),
    playGameScenario.inject(
      constantUsersPerSec(1) during (5 minutes)
    ),
    listGamesScenario.inject(
      constantUsersPerSec(3) during (5 minutes)
    ),
    endToEndScenario.inject(
      constantUsersPerSec(1) during (5 minutes)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(1000),
      global.successfulRequests.percent.gte(95)
    )
}
