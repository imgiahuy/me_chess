package player

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}
import shared.service.ServiceBootstrap

/** Entry point for the Player microservice.
 *
 * Runs independently of the main REST API on its own port (default 8090).
 * Registers and manages chess player profiles, ratings, and statistics.
 */
object PlayerServiceMain {

  def main(args: Array[String]): Unit = run()

  def run(host: String = "0.0.0.0", port: Int = 8090): Unit = {
    implicit val system: ActorSystem[Unit] = ServiceBootstrap.createActorSystem("player-service")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    println("[INFO] Starting Player microservice...")

    val repo: PlayerRepository = if (ServiceBootstrap.isMongoDBEnabled) {
      println("[INFO] MongoDB persistence enabled")
      ServiceBootstrap.connectToMongoDBWithRetry() match {
        case Success(mongoClient) =>
          val databaseName = sys.env.getOrElse("MONGODB_DATABASE", "chess")
          val mongoRepo = new MongoPlayerRepository(mongoClient, databaseName)
          println(s"[INFO] Connected to MongoDB at ${sys.env.getOrElse("MONGODB_HOST", "localhost")}:${sys.env.getOrElse("MONGODB_PORT", "27017")}")
          mongoRepo
        case Failure(e) =>
          println(s"[ERROR] All MongoDB connection attempts failed — PLAYER DATA WILL NOT PERSIST! ${e.getMessage}")
          new PlayerRepository()
      }
    } else {
      println("[INFO] Using in-memory storage (set MONGODB_HOST to enable persistence)")
      new PlayerRepository()
    }
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
