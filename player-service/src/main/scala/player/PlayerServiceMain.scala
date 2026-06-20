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

    val mongoEnabled = sys.env.contains("MONGODB_HOST")
    val repo: PlayerRepository = if (mongoEnabled) {
      println("[INFO] MongoDB persistence enabled")
      val maxRetries = 5
      var attempt = 0
      var connected: Option[MongoPlayerRepository] = None
      while (attempt < maxRetries && connected.isEmpty) {
        attempt += 1
        try {
          val mongoRepo = MongoPlayerRepository.fromEnv()
          println(s"[INFO] Connected to MongoDB at ${sys.env.getOrElse("MONGODB_HOST", "localhost")}:${sys.env.getOrElse("MONGODB_PORT", "27017")}")
          connected = Some(mongoRepo)
        } catch {
          case e: Exception =>
            println(s"[WARN] MongoDB connection attempt $attempt/$maxRetries failed: ${e.getMessage}")
            if (attempt < maxRetries) {
              println(s"[INFO] Retrying in ${attempt * 2} seconds...")
              Thread.sleep(attempt * 2000L)
            }
        }
      }
      connected.getOrElse {
        println("[ERROR] All MongoDB connection attempts failed — PLAYER DATA WILL NOT PERSIST!")
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
