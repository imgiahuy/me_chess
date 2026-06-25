package tournament

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import tournament.api.TournamentRoutes
import tournament.kafka.TournamentKafkaService
import tournament.persistence.{InMemoryTournamentRepository, MongoTournamentRepository, TournamentRepository}
import tournament.auth.JwtService
import shared.service.ServiceBootstrap

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
    implicit val system: ActorSystem[Unit] = ServiceBootstrap.createActorSystem("tournament-service")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    println("[INFO] Starting Tournament microservice...")

    val repo: TournamentRepository = if ServiceBootstrap.isMongoDBEnabled then
      println("[INFO] MongoDB persistence enabled")
      ServiceBootstrap.connectToMongoDBWithRetry() match
        case Success(mongoClient) =>
          val databaseName = sys.env.getOrElse("MONGODB_DATABASE", "chess")
          val mongoRepo = new MongoTournamentRepository(mongoClient, databaseName)
          println(s"[INFO] Connected to MongoDB at ${sys.env.getOrElse("MONGODB_HOST", "localhost")}:${sys.env.getOrElse("MONGODB_PORT", "27017")}")
          mongoRepo
        case Failure(e) =>
          println(s"[ERROR] All MongoDB connection attempts failed — TOURNAMENT DATA WILL NOT PERSIST! ${e.getMessage}")
          new InMemoryTournamentRepository()
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
