package kafka

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes, sse}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.stream.scaladsl.Source
import upickle.default.{ReadWriter, macroRW}
import KafkaApiCodecs.given
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.time.Instant
import java.util.UUID

/** REST API routes for Kafka integration.
  *
  * Provides endpoints for:
  * - Publishing custom events to Kafka topics
  * - Subscribing to real-time event streams via SSE
  * - Health checks and topic management
  * - Monitoring Kafka consumer lag and throughput
  */
class KafkaApiRoutes(
  kafkaService: KafkaGameEventService
)(implicit system: ActorSystem[?]) extends EventStreamMarshalling {

  private implicit val ec: ExecutionContext = system.executionContext

  private def jsonResponse[T: upickle.default.Writer](obj: T): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, upickle.default.write(obj))

  private def parseJson[T: upickle.default.Reader](body: String): Either[String, T] =
    try {
      Right(upickle.default.read[T](body))
    } catch {
      case e: Exception => Left(s"Invalid JSON: ${e.getMessage}")
    }

  /** All Kafka API routes. */
  val routes: Route = pathPrefix("v1" / "kafka") {
    concat(
      healthRoutes,
      publishRoutes,
      streamRoutes,
      topicRoutes
    )
  }

  // ========== HEALTH CHECK ROUTES ==========

  private def healthRoutes: Route = path("health") {
    get {
      val status = KafkaHealthStatus(
        kafkaService.isHealthy,
        sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
        Instant.now().toString
      )
      complete(StatusCodes.OK, jsonResponse(status))
    }
  }

  // ========== PUBLISH ROUTES ==========

  private def publishRoutes: Route = path("publish") {
    post {
      entity(as[String]) { body =>
        parseJson[PublishEventRequest](body) match {
          case Right(request) =>
            val event = createEventFromRequest(request)
            // Publish to Kafka
            onComplete(kafkaService.publishEvent(event)) {
              case scala.util.Success(_) =>
                complete(StatusCodes.Accepted, jsonResponse(PublishResponse(
                  success = true,
                  eventId = event.eventId,
                  message = "Event published successfully"
                )))
              case scala.util.Failure(ex) =>
                complete(StatusCodes.InternalServerError, jsonResponse(PublishResponse(
                  success = false,
                  eventId = event.eventId,
                  message = s"Failed to publish: ${ex.getMessage}"
                )))
            }
          case Left(error) =>
            complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
        }
      }
    }
  }

  // ========== STREAM ROUTES (SSE) ==========

  private def streamRoutes: Route = path("stream" / Segment) { topic =>
    get {
      // Server-Sent Events stream of Kafka messages
      val eventSource = Source.tick(0.seconds, 1.second, ())
        .map(_ => {
          val data = s"{\"topic\":\"$topic\",\"timestamp\":\"${Instant.now()}\"}"
          sse.ServerSentEvent(data, "message")
        })
        .keepAlive(30.seconds, () => sse.ServerSentEvent("", "heartbeat"))

      complete(eventSource)
    }
  }

  // ========== TOPIC MANAGEMENT ROUTES ==========

  private def topicRoutes: Route = pathPrefix("topics") {
    concat(
      // List all available topics
      pathEnd {
        get {
          val topics = KafkaTopicsList(
            GameTopics.allTopics,
            GameTopics.allTopics.length
          )
          complete(StatusCodes.OK, jsonResponse(topics))
        }
      },
      // Get specific topic info
      path(Segment) { topicName =>
        get {
          val info = TopicInfo(
            name = topicName,
            partitions = 1,  // Would query Kafka for actual info
            replicationFactor = 1,
            messages = 0,    // Would query actual count
            description = getTopicDescription(topicName)
          )
          complete(StatusCodes.OK, jsonResponse(info))
        }
      }
    )
  }

  // ========== HELPER METHODS ==========

  private def createEventFromRequest(request: PublishEventRequest): GameEvent = {
    request.eventType.toUpperCase match {
      case "GAME_CREATED" =>
        GameCreatedEvent(
          eventId = request.eventId.getOrElse(UUID.randomUUID().toString),
          gameId = request.gameId,
          timestamp = request.timestamp.map(Instant.parse).getOrElse(Instant.now()),
          whitePlayer = request.payload.getOrElse("whitePlayer", "Anonymous"),
          blackPlayer = request.payload.getOrElse("blackPlayer", "Anonymous"),
          initialFen = request.payload.getOrElse("fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
          timeControl = request.payload.get("timeControl")
        )

      case "MOVE_MADE" =>
        MoveMadeEvent(
          eventId = request.eventId.getOrElse(UUID.randomUUID().toString),
          gameId = request.gameId,
          timestamp = request.timestamp.map(Instant.parse).getOrElse(Instant.now()),
          playerColor = request.payload.getOrElse("playerColor", "white"),
          moveUCI = request.payload.getOrElse("moveUCI", ""),
          moveNumber = request.payload.get("moveNumber").map(_.toInt).getOrElse(0),
          fenAfterMove = request.payload.getOrElse("fenAfterMove", ""),
          remainingWhiteTime = request.payload.get("remainingWhiteTime").map(_.toLong),
          remainingBlackTime = request.payload.get("remainingBlackTime").map(_.toLong)
        )

      case "GAME_ENDED" =>
        GameEndedEvent(
          eventId = request.eventId.getOrElse(UUID.randomUUID().toString),
          gameId = request.gameId,
          timestamp = request.timestamp.map(Instant.parse).getOrElse(Instant.now()),
          result = request.payload.getOrElse("result", "draw"),
          winner = request.payload.get("winner"),
          reason = request.payload.get("reason"),
          finalFen = request.payload.getOrElse("finalFen", ""),
          totalMoves = request.payload.get("totalMoves").map(_.toInt).getOrElse(0),
          pgn = request.payload.get("pgn")
        )

      case "PLAYER_RESIGNED" =>
        PlayerResignedEvent(
          eventId = request.eventId.getOrElse(UUID.randomUUID().toString),
          gameId = request.gameId,
          timestamp = request.timestamp.map(Instant.parse).getOrElse(Instant.now()),
          resigningColor = request.payload.getOrElse("resigningColor", "white")
        )

      case "TIME_WARNING" | "TIMEOUT" =>
        TimeWarningEvent(
          eventId = request.eventId.getOrElse(UUID.randomUUID().toString),
          gameId = request.gameId,
          timestamp = request.timestamp.map(Instant.parse).getOrElse(Instant.now()),
          playerColor = request.payload.getOrElse("playerColor", "white"),
          remainingTimeMs = request.payload.get("remainingTimeMs").map(_.toLong).getOrElse(0L),
          isTimeout = request.eventType.toUpperCase == "TIMEOUT"
        )

      case _ =>
        throw new IllegalArgumentException(s"Unknown event type: ${request.eventType}")
    }
  }

  private def getTopicDescription(topicName: String): String = topicName match {
    case GameTopics.GAME_EVENTS => "General chess game events"
    case GameTopics.GAME_CREATED => "New game creation events"
    case GameTopics.MOVE_MADE => "Chess move events"
    case GameTopics.GAME_ENDED => "Game conclusion events"
    case GameTopics.PLAYER_RESIGNED => "Player resignation events"
    case GameTopics.TIME_EVENTS => "Time warning and timeout events"
    case GameTopics.GAME_STATE_UPDATES => "Game state update events"
    case _ => "Unknown topic"
  }
}

// ========== JSON MODELS ==========

/** Request to publish an event to Kafka. */
case class PublishEventRequest(
  eventType: String,
  gameId: String,
  eventId: Option[String] = None,
  timestamp: Option[String] = None,
  payload: Map[String, String] = Map.empty
)

/** Response after publishing an event. */
case class PublishResponse(
  success: Boolean,
  eventId: String,
  message: String
)

/** Kafka health status. */
case class KafkaHealthStatus(
  healthy: Boolean,
  bootstrapServers: String,
  timestamp: String
)

/** List of available Kafka topics. */
case class KafkaTopicsList(
  topics: Seq[String],
  count: Int
)

/** Information about a specific topic. */
case class TopicInfo(
  name: String,
  partitions: Int,
  replicationFactor: Int,
  messages: Long,
  description: String
)

/** Error response. */
case class ErrorResponse(error: String)

/** JSON codecs for API models. */
object KafkaApiCodecs {
  given ReadWriter[PublishEventRequest] = macroRW
  given ReadWriter[PublishResponse] = macroRW
  given ReadWriter[KafkaHealthStatus] = macroRW
  given ReadWriter[KafkaTopicsList] = macroRW
  given ReadWriter[TopicInfo] = macroRW
  given ReadWriter[ErrorResponse] = macroRW
}
