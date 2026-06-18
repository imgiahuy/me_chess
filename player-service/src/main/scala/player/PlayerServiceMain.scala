package player

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/** Entry point for the Player microservice.
 *
 * Runs independently of the main REST API on its own port (default 8090).
 * Registers and manages chess player profiles, ratings, and statistics.
 */
object PlayerServiceMain {

  def main(args: Array[String]): Unit = run()

  def run(host: String = "0.0.0.0", port: Int = 8090): Unit = {
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "player-service")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    println("[INFO] Starting Player microservice...")

    val repo   = new PlayerRepository()
    val routes = new PlayerRoutes(repo).routes

    Http().newServerAt(host, port).bind(routes).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        println(s"[SUCCESS] Player service running at http://${addr.getHostString}:${addr.getPort}")
        println(s"[INFO]    POST   /v1/players          - create player")
        println(s"[INFO]    GET    /v1/players          - list players")
        println(s"[INFO]    GET    /v1/players/{{id}}    - get player")
        println(s"[INFO]    GET    /v1/players/leaderboard")
        println(s"[INFO]    GET    /v1/players/health")

        sys.addShutdownHook {
          binding.unbind()
          system.terminate()
        }
        while (true) Thread.sleep(1000)

      case Failure(ex) =>
        println(s"[ERROR] Failed to bind player service: ${ex.getMessage}")
        system.terminate()
    }
  }
}
