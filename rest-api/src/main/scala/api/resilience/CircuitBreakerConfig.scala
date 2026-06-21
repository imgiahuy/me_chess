package api.resilience

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/** Configuration for circuit breaker resilience pattern. */
case class CircuitBreakerConfig(
    maxFailures: Int = 5,
    callTimeout: FiniteDuration = 10.seconds,
    resetTimeout: FiniteDuration = 30.seconds,
    maxRetries: Int = 3
)

object CircuitBreakerConfig {
  val default: CircuitBreakerConfig = CircuitBreakerConfig()
  val aggressive: CircuitBreakerConfig = CircuitBreakerConfig(
    maxFailures = 3,
    callTimeout = 5.seconds,
    resetTimeout = 15.seconds,
    maxRetries = 2
  )
  val lenient: CircuitBreakerConfig = CircuitBreakerConfig(
    maxFailures = 10,
    callTimeout = 30.seconds,
    resetTimeout = 60.seconds,
    maxRetries = 5
  )
}

/** Circuit breaker manager for protecting external service calls. */
class CircuitBreakerManager(config: CircuitBreakerConfig)(implicit system: ActorSystem[?], ec: ExecutionContext) {
  
  private val classicSystem = system.toClassic
  private val scheduler = classicSystem.scheduler
  
  val circuitBreaker = CircuitBreaker(
    scheduler = scheduler,
    maxFailures = config.maxFailures,
    callTimeout = config.callTimeout,
    resetTimeout = config.resetTimeout
  )
  
  /** Execute a call with circuit breaker protection and retry logic. */
  def withProtection[T](call: => Future[T])(fallback: => Future[T]): Future[T] = {
    withRetry(
      circuitBreaker.withCircuitBreaker(call).recoverWith {
        case _: CircuitBreakerOpenException =>
          // Circuit is open, use fallback immediately
          fallback
        case _: Exception =>
          // Call failed but circuit is not open yet, will be counted
          Future.failed(new Exception("Call failed"))
      }
    )
  }
  
  /** Execute a call with exponential backoff retry logic. */
  private def withRetry[T](call: => Future[T]): Future[T] = {
    retry(call, config.maxRetries, 1.second)
  }
  
  private def retry[T](call: => Future[T], remainingRetries: Int, delay: FiniteDuration): Future[T] = {
    call.recoverWith {
      case _: Exception if remainingRetries > 0 =>
        Thread.sleep(delay.toMillis)
        retry(call, remainingRetries - 1, delay * 2) // Exponential backoff
      case e: Exception =>
        Future.failed(e)
    }
  }
  
  /** Get current circuit breaker state for monitoring. */
  def getState: String = circuitBreaker.toString
}
