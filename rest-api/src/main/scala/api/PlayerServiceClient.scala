package api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import api.resilience.{CircuitBreakerConfig, CircuitBreakerManager}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import upickle.default._

/** HTTP client for communicating with the player-service microservice.
 * Includes circuit breaker protection and retry logic for resilience.
 */
class PlayerServiceClient(
    baseUrl: String = "http://player-service:8090",
    circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig.default
)(implicit system: ActorSystem[?], ec: ExecutionContext) {

  private val httpClient = Http()
  private val circuitBreakerManager = new CircuitBreakerManager(circuitBreakerConfig)

  /** Get or create a player by username. Returns the player ID.
   * Uses circuit breaker protection with fallback to direct creation.
   */
  def getOrCreatePlayer(username: String): Future[String] = {
    circuitBreakerManager.withProtection(
      // Protected call
      {
        val findUrl = s"$baseUrl/v1/players"
        val request = HttpRequest(uri = findUrl)
        
        httpClient.singleRequest(request).flatMap { response =>
          if (response.status == StatusCodes.OK) {
            Unmarshal(response.entity).to[String].map { body =>
              val players = read[PlayerListResponse](body)
              players.players.find(_.username == username) match {
                case Some(p) => Future.successful(p.id)
                case None => createPlayer(username)
              }
            }.flatten
          } else {
            // If list fails, try to create directly
            createPlayer(username)
          }
        }
      }
    )(
      // Fallback: try to create directly without listing
      createPlayer(username)
    )
  }

  /** Create a new player with the given username. Returns the player ID.
   * Uses circuit breaker protection with fallback to failure.
   */
  private def createPlayer(username: String): Future[String] = {
    circuitBreakerManager.withProtection(
      {
        val createUrl = s"$baseUrl/v1/players"
        val requestBody = write(CreatePlayerRequest(username, None, 1200))
        val request = HttpRequest(
          method = HttpMethods.POST,
          uri = createUrl,
          entity = HttpEntity(ContentTypes.`application/json`, requestBody)
        )

        httpClient.singleRequest(request).flatMap { response =>
          if (response.status == StatusCodes.Created) {
            Unmarshal(response.entity).to[String].map { body =>
              val player = read[Player](body)
              player.id
            }
          } else {
            Unmarshal(response.entity).to[String].flatMap { body =>
              Future.failed(new Exception(s"Failed to create player: ${response.status} - $body"))
            }
          }
        }
      }
    )(
      // Fallback: return a generated ID as fallback
      Future.successful(s"fallback-${username}-${System.currentTimeMillis()}")
    )
  }

  /** Record a game result for a player.
   * Uses circuit breaker protection with silent fallback (game recording is non-critical).
   */
  def recordGameResult(playerId: String, opponentId: String, result: String, ratingChange: Int): Future[Unit] = {
    circuitBreakerManager.withProtection(
      {
        val url = s"$baseUrl/v1/players/$playerId/games"
        val requestBody = write(RecordGameRequest(opponentId, result, ratingChange))
        val request = HttpRequest(
          method = HttpMethods.POST,
          uri = url,
          entity = HttpEntity(ContentTypes.`application/json`, requestBody)
        )
        
        httpClient.singleRequest(request).flatMap { response =>
          if (response.status.isSuccess()) {
            Future.successful(())
          } else {
            Unmarshal(response.entity).to[String].flatMap { body =>
              Future.failed(new Exception(s"Failed to record game: ${response.status} - $body"))
            }
          }
        }
      }
    )(
      // Fallback: silently ignore game recording failures
      Future.successful(())
    )
  }

  /** Shutdown the HTTP client. */
  def shutdown(): Future[Unit] = httpClient.shutdownAllConnectionPools()
}

// JSON request/response models for player-service
case class CreatePlayerRequest(username: String, email: Option[String], initialRating: Int)
case class RecordGameRequest(opponentId: String, result: String, ratingChange: Int)
case class PlayerListResponse(players: List[Player], total: Int = 0)
case class Player(id: String, username: String, email: Option[String], rating: Int, gamesPlayed: Int, wins: Int, losses: Int, draws: Int, createdAt: String, lastSeenAt: String)

given ReadWriter[CreatePlayerRequest] = macroRW
given ReadWriter[RecordGameRequest] = macroRW
given ReadWriter[Player] = macroRW
given ReadWriter[PlayerListResponse] = macroRW
