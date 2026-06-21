package tournament

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import tournament.api.TournamentRoutes
import tournament.kafka.TournamentKafkaService
import tournament.persistence.{InMemoryTournamentRepository, MongoTournamentRepository, TournamentRepository}
import tournament.auth.JwtService

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/** Entry point for the Tournament microservice.
 *
 * Manages tournaments, bot/player registration, pairings, results, and standings.
 * Publishes lifecycle events to Kafka when KAFKA_ENABLED is true.
 */
object TournamentServiceMain:

  def main(args: Array[String]): Unit = run()

  def run(host: String = "0.0.0.0", port: Int = sys.env.getOrElse("SERVICE_PORT", "8070").toInt): Unit =
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "tournament-service")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    println("[INFO] Starting Tournament microservice...")

    val repo: TournamentRepository = if sys.env.contains("MONGODB_HOST") then
      println("[INFO] MongoDB persistence enabled")
      val maxRetries = 5
      var attempt = 0
      var connected: Option[MongoTournamentRepository] = None
      while attempt < maxRetries && connected.isEmpty do
        attempt += 1
        try
          val mongoRepo = MongoTournamentRepository.fromEnv()
          println(s"[INFO] Connected to MongoDB at ${sys.env.getOrElse("MONGODB_HOST", "localhost")}:${sys.env.getOrElse("MONGODB_PORT", "27017")}")
          connected = Some(mongoRepo)
        catch
          case e: Exception =>
            println(s"[WARN] MongoDB connection attempt $attempt/$maxRetries failed: ${e.getMessage}")
            if attempt < maxRetries then
              println(s"[INFO] Retrying in ${attempt * 2} seconds...")
              Thread.sleep(attempt * 2000L)
      connected.getOrElse {
        println("[ERROR] All MongoDB connection attempts failed — TOURNAMENT DATA WILL NOT PERSIST!")
        new InMemoryTournamentRepository()
      }
    else
      println("[INFO] Using in-memory storage (set MONGODB_HOST to enable persistence)")
      new InMemoryTournamentRepository()

    val kafka = TournamentKafkaService.fromEnv()
    val jwtService = JwtService.create()
    val routes = new TournamentRoutes(repo, kafka, jwtService).routes

    Http().newServerAt(host, port).bind(routes).onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        println(s"[SUCCESS] Tournament service running at http://${addr.getHostString}:${addr.getPort}")
        println("[INFO]    POST   /v1/tournaments/auth/register - register bot and get JWT token")
        println("[INFO]    POST   /v1/tournaments/auth/validate - validate JWT token")
        println("[INFO]    POST   /v1/tournaments              - create tournament")
        println("[INFO]    GET    /v1/tournaments              - list tournaments")
        println("[INFO]    POST   /v1/tournaments/{id}/register - register participant")
        println("[INFO]    POST   /v1/tournaments/{id}/start   - start tournament")
        println("[INFO]    POST   /v1/tournaments/{id}/result  - report game result")
        println("[INFO]    GET    /v1/tournaments/{id}/standings")
        println("[INFO]    GET    /v1/tournaments/{id}/stream  - SSE tournament events")
        println("[INFO]    GET    /v1/tournaments/{id}/game/{gameId}/stream - SSE game events")
        println("[INFO]    GET    /health")

        sys.addShutdownHook {
          kafka.shutdown()
          binding.unbind()
          system.terminate()
        }
        while (true) Thread.sleep(1000)

      case Failure(ex) =>
        println(s"[ERROR] Failed to bind tournament service: ${ex.getMessage}")
        kafka.shutdown()
        system.terminate()
    }
