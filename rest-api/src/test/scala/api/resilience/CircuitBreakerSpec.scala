package api.resilience

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class CircuitBreakerSpec extends AnyFunSuite with Matchers {

  implicit val system: ActorSystem[?] = ActorSystem(Behaviors.empty, "test-system")

  test("CircuitBreakerConfig default values") {
    val config = CircuitBreakerConfig.default
    config.maxFailures shouldEqual 5
    config.callTimeout shouldEqual 10.seconds
    config.resetTimeout shouldEqual 30.seconds
    config.maxRetries shouldEqual 3
  }

  test("CircuitBreakerConfig aggressive preset") {
    val config = CircuitBreakerConfig.aggressive
    config.maxFailures shouldEqual 3
    config.callTimeout shouldEqual 5.seconds
    config.resetTimeout shouldEqual 15.seconds
    config.maxRetries shouldEqual 2
  }

  test("CircuitBreakerConfig lenient preset") {
    val config = CircuitBreakerConfig.lenient
    config.maxFailures shouldEqual 10
    config.callTimeout shouldEqual 30.seconds
    config.resetTimeout shouldEqual 60.seconds
    config.maxRetries shouldEqual 5
  }

  test("CircuitBreakerManager can be instantiated") {
    val config = CircuitBreakerConfig.default
    val manager = new CircuitBreakerManager(config)
    manager should not be null
  }

  test("CircuitBreakerManager returns state string") {
    val config = CircuitBreakerConfig.default
    val manager = new CircuitBreakerManager(config)
    
    val state = manager.getState
    state should not be empty
  }
}
