package performance

import io.gatling.core.Predef._
import scala.concurrent.duration._

class EndToEndChessSimulation extends BaseChessSimulation {

  setUp(
    endToEndScenario.inject(
      rampUsersPerSec(1) to 5 during (60 seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lte(1000),
      global.responseTime.percentile4.lte(2000),
      global.successfulRequests.percent.gte(95)
    )
}
