package player

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes, headers}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import upickle.default.{ReadWriter, macroRW, write, read}
import java.time.Instant

/** REST API routes for the Player microservice.
 *
 * Endpoints:
 *   POST   /v1/players                          - Create player
 *   GET    /v1/players                          - List all players
 *   GET    /v1/players/{id}                     - Get player by ID
 *   PUT    /v1/players/{id}                     - Update player
 *   DELETE /v1/players/{id}                     - Delete player
 *   POST   /v1/players/{id}/games               - Record a game result
 *   GET    /v1/players/leaderboard              - Player leaderboard
 *   GET    /v1/players/health                   - Health check
 */
class PlayerRoutes(repo: PlayerRepository)(implicit system: ActorSystem[?]) {

  import PlayerJsonCodecs.given

  private def jsonResponse[T: upickle.default.Writer](obj: T): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, write(obj))

  private def parseJson[T: upickle.default.Reader](body: String): Either[String, T] =
    try Right(read[T](body))
    catch { case e: Exception => Left(s"Invalid JSON: ${e.getMessage}") }

  private def corsHeaders: List[HttpHeader] = List(
    headers.`Access-Control-Allow-Origin`.*,
    headers.`Access-Control-Allow-Methods`(
      akka.http.scaladsl.model.HttpMethods.GET,
      akka.http.scaladsl.model.HttpMethods.POST,
      akka.http.scaladsl.model.HttpMethods.PUT,
      akka.http.scaladsl.model.HttpMethods.DELETE,
      akka.http.scaladsl.model.HttpMethods.OPTIONS
    ),
    headers.`Access-Control-Allow-Headers`("Content-Type", "Authorization"),
    headers.`Access-Control-Max-Age`(3600)
  )

  val routes: Route = respondWithHeaders(corsHeaders) {
    pathPrefix("v1" / "players") {
      concat(
        // ─── Health ──────────────────────────────────────────────────────────
        path("health") {
          get {
            val totalPlayers = repo.findAll().length
            complete(jsonResponse(Map(
              "status" -> "UP",
              "service" -> "player-service",
              "version" -> "1.0.0",
              "totalPlayers" -> totalPlayers.toString,
              "timestamp" -> Instant.now().toString
            )))
          }
        },

        // ─── Leaderboard ─────────────────────────────────────────────────────
        path("leaderboard") {
          get {
            val stats = repo.leaderboard()
            complete(jsonResponse(PlayerStatsListResponse(stats, Instant.now().toString)))
          }
        },

        // ─── Create / List ────────────────────────────────────────────────────
        pathEnd {
          concat(
            post {
              entity(as[String]) { body =>
                parseJson[CreatePlayerRequest](body) match {
                  case Right(req) =>
                    if (req.username.trim.isEmpty)
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse("username cannot be empty")))
                    else repo.findByUsername(req.username) match {
                      case Some(_) =>
                        complete(StatusCodes.Conflict, jsonResponse(ErrorResponse(s"Username '${req.username}' already taken")))
                      case None =>
                        val player = repo.create(req)
                        complete(StatusCodes.Created, jsonResponse(player))
                    }
                  case Left(err) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
                }
              }
            },
            get {
              val players = repo.findAll()
              complete(jsonResponse(PlayerListResponse(players, players.length)))
            }
          )
        },

        // ─── Single player operations ─────────────────────────────────────────
        path(Segment) { id =>
          concat(
            get {
              repo.findById(id) match {
                case Some(p) => complete(jsonResponse(p))
                case None    => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Player not found: $id")))
              }
            },
            put {
              entity(as[String]) { body =>
                parseJson[UpdatePlayerRequest](body) match {
                  case Right(req) =>
                    repo.update(id, req) match {
                      case Some(p) => complete(jsonResponse(p))
                      case None    => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Player not found: $id")))
                    }
                  case Left(err) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
                }
              }
            },
            delete {
              if (repo.delete(id))
                complete(jsonResponse(ActionResponse(s"Player $id deleted")))
              else
                complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Player not found: $id")))
            }
          )
        },

        // ─── Record game result ───────────────────────────────────────────────
        path(Segment / "games") { id =>
          post {
            entity(as[String]) { body =>
              parseJson[RecordGameRequest](body) match {
                case Right(req) =>
                  repo.recordGame(req.copy(playerId = id)) match {
                    case Some(p) => complete(jsonResponse(p))
                    case None    => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Player not found: $id")))
                  }
                case Left(err) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
              }
            }
          }
        }
      )
    }
  }
}

/** JSON codecs for player service models. */
object PlayerJsonCodecs {
  given ReadWriter[Player]                  = macroRW
  given ReadWriter[CreatePlayerRequest]     = macroRW
  given ReadWriter[UpdatePlayerRequest]     = macroRW
  given ReadWriter[RecordGameRequest]       = macroRW
  given ReadWriter[PlayerStats]             = macroRW
  given ReadWriter[PlayerListResponse]      = macroRW
  given ReadWriter[PlayerStatsListResponse] = macroRW
  given ReadWriter[ActionResponse]          = macroRW
  given ReadWriter[ErrorResponse]           = macroRW

  given optionStringRW: ReadWriter[Option[String]] = upickle.default.readwriter[ujson.Value].bimap(
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

  given optionIntRW: ReadWriter[Option[Int]] = upickle.default.readwriter[ujson.Value].bimap(
    {
      case Some(n) => ujson.Num(n)
      case None    => ujson.Null
    },
    {
      case ujson.Null   => None
      case ujson.Num(n) => Some(n.toInt)
      case other        => None
    }
  )
}
