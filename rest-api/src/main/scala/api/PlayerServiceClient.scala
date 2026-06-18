package api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import upickle.default._

/** HTTP client for communicating with the player-service microservice. */
class PlayerServiceClient(baseUrl: String = "http://player-service:8090")(implicit system: ActorSystem[?], ec: ExecutionContext) {

  private val httpClient = Http()

  /** Get or create a player by username. Returns the player ID. */
  def getOrCreatePlayer(username: String): Future[String] = {
    // First try to find by username
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

  /** Create a new player with the given username. Returns the player ID. */
  private def createPlayer(username: String): Future[String] = {
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

  /** Record a game result for a player. */
  def recordGameResult(playerId: String, opponentId: String, result: String, ratingChange: Int): Future[Unit] = {
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

  /** Shutdown the HTTP client. */
  def shutdown(): Future[Unit] = httpClient.shutdownAllConnectionPools()
}

// JSON request/response models for player-service
case class CreatePlayerRequest(username: String, email: Option[String], initialRating: Int)
case class RecordGameRequest(opponentId: String, result: String, ratingChange: Int)
case class PlayerListResponse(players: List[Player])
case class Player(id: String, username: String, email: Option[String], rating: Int, gamesPlayed: Int, wins: Int, losses: Int, draws: Int, createdAt: String, lastSeenAt: String)

// Custom Option[String] codec to serialize None as null instead of []
given optionStringRW: ReadWriter[Option[String]] = readwriter[ujson.Value].bimap(
  {
    case Some(s) => ujson.Str(s)
    case None    => ujson.Null
  },
  {
    case ujson.Null   => None
    case ujson.Str(s) => Some(s)
    case other        => Some(other.str)
  }
)

given ReadWriter[CreatePlayerRequest] = macroRW
given ReadWriter[RecordGameRequest] = macroRW
given ReadWriter[Player] = macroRW
given ReadWriter[PlayerListResponse] = macroRW
