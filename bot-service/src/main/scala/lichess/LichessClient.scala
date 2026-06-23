package lichess

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.scaladsl.{Framing, Source}
import akka.util.ByteString
import upickle.default.write

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import LichessJsonCodecs.given

/** HTTP client for the Lichess Bot API.
  *
  * Streams account events and per-game events via NDJSON and exposes the
  * commands needed to accept challenges and play moves.
  */
class LichessClient(config: LichessBotConfig)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext
  private val http = Http()

  private def baseRequest(path: String): HttpRequest = {
    HttpRequest(
      uri = s"${config.baseUrl}$path"
    ).withHeaders(
      Authorization(OAuth2BearerToken(config.apiToken)),
      headers.Accept(MediaTypes.`application/json`)
    )
  }

  /** Stream of account-level events (challenge, gameStart, gameFinish, ...). */
  def streamEvents(): Source[LichessEvent, Future[NotUsed]] = {
    val request = baseRequest("/api/stream/event")
      .withMethod(HttpMethods.GET)

    Source.futureSource(
      http
        .singleRequest(request)
        .flatMap { response =>
          if (response.status.isSuccess()) {
            Future.successful(
              response.entity.dataBytes
                .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true))
                .map(_.utf8String)
                .filter(_.trim.nonEmpty)
                .map { line =>
                  println(s"[LichessClient] Received event line: $line")
                  try {
                    upickle.default.read[LichessEvent](line)
                  } catch {
                    case e: Exception =>
                      println(s"[LichessClient] Failed to parse event: $line - ${e.getMessage}")
                      LichessEvent("unknown")
                  }
                }
                .mapMaterializedValue(_ => NotUsed)
            )
          } else {
            response.entity.toStrict(5.seconds).flatMap { entity =>
              Future.failed(new RuntimeException(s"Event stream failed: ${response.status} - ${entity.data.utf8String}"))
            }
          }
        }
    )
  }

  /** Stream of events for a single game. */
  def streamGame(gameId: String): Source[GameStreamEvent, Future[NotUsed]] = {
    val request = baseRequest(s"/api/bot/game/stream/$gameId")
      .withMethod(HttpMethods.GET)

    Source.futureSource(
      http
        .singleRequest(request)
        .flatMap { response =>
          if (response.status.isSuccess()) {
            Future.successful(
              response.entity.dataBytes
                .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true))
                .map(_.utf8String)
                .filter(_.trim.nonEmpty)
                .map { line =>
                  println(s"[LichessClient] Received game event line: $line")
                  try {
                    upickle.default.read[GameStreamEvent](line)
                  } catch {
                    case e: Exception =>
                      println(s"[LichessClient] Failed to parse game event: $line - ${e.getMessage}")
                      UnknownEvent(line)
                  }
                }
                .mapMaterializedValue(_ => NotUsed)
            )
          } else {
            response.entity.toStrict(5.seconds).flatMap { entity =>
              Future.failed(new RuntimeException(s"Game stream failed: ${response.status} - ${entity.data.utf8String}"))
            }
          }
        }
    )
  }

  /** Accept a challenge. */
  def acceptChallenge(challengeId: String): Future[Unit] = {
    post(s"/api/challenge/$challengeId/accept")
  }

  /** Decline a challenge. */
  def declineChallenge(challengeId: String, reason: String = "generic"): Future[Unit] = {
    post(s"/api/challenge/$challengeId/decline", Some(write(ujson.Obj("reason" -> reason))))
  }

  /** Submit a move in UCI notation. */
  def makeMove(gameId: String, move: String): Future[Unit] = {
    post(s"/api/bot/game/$gameId/move/$move")
  }

  /** Resign the current game. */
  def resign(gameId: String): Future[Unit] = {
    post(s"/api/bot/game/$gameId/resign")
  }

  /** Abort the current game. */
  def abort(gameId: String): Future[Unit] = {
    post(s"/api/bot/game/$gameId/abort")
  }

  private def post(path: String, body: Option[String] = None): Future[Unit] = {
    val entity = body match {
      case Some(json) => HttpEntity(ContentTypes.`application/json`, json)
      case None       => HttpEntity.Empty
    }
    val request = baseRequest(path).withMethod(HttpMethods.POST).withEntity(entity)

    http.singleRequest(request).flatMap { response =>
      response.status match {
        case status if status.isSuccess() =>
          response.discardEntityBytes().future().map(_ => ())
        case _ =>
          response.entity.toStrict(5.seconds).flatMap { entity =>
            Future.failed(new RuntimeException(s"Request failed: ${response.status} - ${entity.data.utf8String}"))
          }
      }
    }
  }

  /** Gracefully close the underlying HTTP connection pools. */
  def shutdown(): Future[Unit] = http.shutdownAllConnectionPools()
}
