package api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes, headers}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Token-bucket rate limiter per client IP for Akka HTTP routes.
 *
 * Each IP gets `maxRequests` tokens that refill at a steady rate.
 * When tokens are exhausted, requests receive HTTP 429 Too Many Requests.
 *
 * @param maxRequests   Maximum burst size (tokens per bucket)
 * @param windowMs      Time window in milliseconds over which tokens refill
 */
class RateLimiter(maxRequests: Int = 100, windowMs: Long = 60000) {

  private case class Bucket(tokens: AtomicLong, lastRefill: AtomicLong)

  private val buckets = new ConcurrentHashMap[String, Bucket]()

  private def getBucket(key: String): Bucket =
    buckets.computeIfAbsent(key, _ => Bucket(new AtomicLong(maxRequests), new AtomicLong(System.currentTimeMillis())))

  private def tryConsume(key: String): Boolean = {
    val bucket = getBucket(key)
    val now = System.currentTimeMillis()
    val elapsed = now - bucket.lastRefill.get()

    // Refill tokens based on elapsed time
    if (elapsed > 0) {
      val refill = (elapsed.toDouble / windowMs * maxRequests).toLong
      if (refill > 0) {
        bucket.lastRefill.set(now)
        val current = bucket.tokens.get()
        val newTokens = math.min(maxRequests.toLong, current + refill)
        bucket.tokens.set(newTokens)
      }
    }

    // Try to consume a token
    var consumed = false
    var current = bucket.tokens.get()
    while (current > 0 && !consumed) {
      consumed = bucket.tokens.compareAndSet(current, current - 1)
      if (!consumed) current = bucket.tokens.get()
    }
    consumed
  }

  /** Wraps a Route with per-IP rate limiting. Returns 429 when limit is exceeded. */
  def rateLimitByIp(inner: Route): Route = {
    extractClientIP { remoteAddress =>
      val ip = remoteAddress.toOption.map(_.getHostAddress).getOrElse("unknown")
      if (tryConsume(ip)) {
        inner
      } else {
        val retryAfter = math.ceil(windowMs.toDouble / 1000).toInt
        respondWithHeader(headers.`Retry-After`(retryAfter)) {
          complete(
            StatusCodes.TooManyRequests,
            HttpEntity(
              ContentTypes.`application/json`,
              s"""{"error":"Rate limit exceeded. Try again in $retryAfter seconds."}"""
            )
          )
        }
      }
    }
  }

  /** Periodically clean up stale buckets (IPs not seen recently). */
  def cleanup(staleAfterMs: Long = 300000): Unit = {
    val now = System.currentTimeMillis()
    buckets.entrySet().removeIf(e => now - e.getValue.lastRefill.get() > staleAfterMs)
  }
}
