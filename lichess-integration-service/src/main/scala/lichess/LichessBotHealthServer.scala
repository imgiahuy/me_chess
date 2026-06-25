package lichess

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/** Minimal HTTP health server for the Lichess bot worker.
 *
 * Provides liveness/readiness endpoints so the bot can be deployed with
 * Docker Compose and Kubernetes health checks even though the main process is
 * an event-driven worker rather than a request-response service.
 */
class LichessBotHealthServer(
  worker: LichessBotWorker,
  host: String = "0.0.0.0",
  port: Int = 8080
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  val route: Route = path("health") {
    get {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """{"status":"UP","service":"lichess-bot","timestamp":""" +
            java.time.Instant.now().toString +
            """"}"""
        )
      )
    }
  } ~ path("info") {
    get {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """{"name":"ME Chess Lichess Bot","version":"1.0.0"}"""
        )
      )
    }
  }

  def start(): Future[Http.ServerBinding] = {
    Http().newServerAt(host, port).bind(route)
  }
}
