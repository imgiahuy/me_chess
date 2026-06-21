package kafka

import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Request-Response pattern implementation for Kafka.
  * 
  * Enables synchronous-style communication over asynchronous Kafka by:
  * - Correlating requests with unique correlation IDs
  * - Maintaining a map of pending requests
  * - Timeout handling for unresponsive requests
  * 
  * Use cases:
  * - AI move calculation requests
  * - Stockfish engine queries
  * - Cross-service queries (player service, analytics service)
  */
class KafkaRequestResponse(
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
  requestTopic: String,
  responseTopic: String,
  groupId: String = "request-response-group",
  requestTimeout: FiniteDuration = 30.seconds
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  // Map of correlation ID -> Promise[Response]
  private val pendingRequests = new ConcurrentHashMap[String, Promise[String]]()

  // Producer settings
  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)
    .withProperty("acks", "all")
    .withProperty("enable.idempotence", "true")

  // Consumer settings
  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId(groupId)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  /** Send a request and wait for response */
  def sendRequest(requestPayload: String): Future[String] = {
    val correlationId = UUID.randomUUID().toString
    val promise = Promise[String]()

    pendingRequests.put(correlationId, promise)

    // Set timeout and store the cancellable
    val timeoutCancellable = system.scheduler.scheduleOnce(
      java.time.Duration.ofMillis(requestTimeout.toMillis),
      new Runnable {
        def run(): Unit = {
          val removedPromise = pendingRequests.remove(correlationId)
          if (removedPromise != null) {
            // Use tryFailure to avoid race condition if response already arrived
            if (!removedPromise.isCompleted) {
              removedPromise.tryFailure(new java.util.concurrent.TimeoutException(s"Request timed out after $requestTimeout"))
            }
          }
        }
      },
      ec
    )

    // Create producer record with correlation ID
    val record = new ProducerRecord[String, String](
      requestTopic,
      correlationId,  // Key is correlation ID
      requestPayload
    )
    record.headers().add("correlation_id", correlationId.getBytes())
    record.headers().add("reply_to", responseTopic.getBytes())

    // Send request
    val source = Source.single(record)
      .runWith(Producer.plainSink(producerSettings))

    source.onComplete {
      case Success(_) =>
        println(s"[KafkaRequestResponse] Request sent: correlationId=$correlationId")
      case Failure(ex) =>
        pendingRequests.remove(correlationId)
        timeoutCancellable.cancel()
        promise.failure(ex)
        println(s"[KafkaRequestResponse] Failed to send request: ${ex.getMessage}")
    }

    promise.future
  }

  /** Start consuming responses */
  def startResponseConsumer(): Future[akka.Done] = {
    println(s"[KafkaRequestResponse] Starting response consumer on topic: $responseTopic")

    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(responseTopic))
      .mapAsync(1) { record =>
        val correlationHeader = record.headers().lastHeader("correlation_id")
        if (correlationHeader == null || correlationHeader.value() == null) {
          println(s"[KafkaRequestResponse] Received response without correlation_id header")
          Future.successful(())
        } else {
          val correlationId = new String(correlationHeader.value())
          val response = record.value()

          Option(pendingRequests.remove(correlationId)) match {
            case Some(promise) =>
              promise.success(response)
              Future.successful(())
            case None =>
              println(s"[KafkaRequestResponse] Received response for unknown correlation ID: $correlationId")
              Future.successful(())
          }
        }
      }
      .runWith(Sink.ignore)
  }

  /** Send request with typed request/response */
  def sendTypedRequest[Req, Res](
    request: Req,
    requestSerializer: Req => String,
    responseDeserializer: String => Res
  ): Future[Res] = {
    val requestPayload = requestSerializer(request)
    sendRequest(requestPayload).map(responseDeserializer)
  }

  /** Cleanup pending requests */
  def shutdown(): Unit = {
    println("[KafkaRequestResponse] Shutting down, cancelling pending requests")
    pendingRequests.forEach { (id, promise) =>
      promise.failure(new IllegalStateException("Request-response service shutting down"))
    }
    pendingRequests.clear()
  }

  /** Get count of pending requests */
  def pendingCount: Int = pendingRequests.size()
}

/** Request-Response pattern for AI move calculation */
class AiMoveRequestResponse(
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
)(implicit system: ActorSystem[?]) {

  private val requestResponse = new KafkaRequestResponse(
    bootstrapServers = bootstrapServers,
    requestTopic = "chess-ai-requests",
    responseTopic = "chess-ai-responses",
    groupId = "ai-move-request-group",
    requestTimeout = 10.seconds
  )

  /** Request AI move calculation */
  def requestAiMove(
    gameId: String,
    fen: String,
    botType: String = "greedy"
  ): Future[String] = {
    val request = s"""{"gameId":"$gameId","fen":"$fen","botType":"$botType"}"""
    requestResponse.sendRequest(request)
  }

  /** Start consuming AI move responses */
  def startConsumer(): Future[akka.Done] = requestResponse.startResponseConsumer()

  /** Shutdown */
  def shutdown(): Unit = requestResponse.shutdown()
}

/** Request-Response pattern for Stockfish engine queries */
class StockfishRequestResponse(
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
)(implicit system: ActorSystem[?]) {

  private val requestResponse = new KafkaRequestResponse(
    bootstrapServers = bootstrapServers,
    requestTopic = "stockfish-engine-requests",
    responseTopic = "stockfish-engine-responses",
    groupId = "stockfish-request-group",
    requestTimeout = 15.seconds
  )

  /** Request Stockfish evaluation */
  def requestEvaluation(
    fen: String,
    depth: Int = 15
  ): Future[String] = {
    val request = s"""{"fen":"$fen","depth":$depth}"""
    requestResponse.sendRequest(request)
  }

  /** Request Stockfish best move */
  def requestBestMove(
    fen: String,
    depth: Int = 15
  ): Future[String] = {
    val request = s"""{"fen":"$fen","depth":$depth,"type":"bestmove"}"""
    requestResponse.sendRequest(request)
  }

  /** Start consuming Stockfish responses */
  def startConsumer(): Future[akka.Done] = requestResponse.startResponseConsumer()

  /** Shutdown */
  def shutdown(): Unit = requestResponse.shutdown()
}

/** Request-Response pattern for cross-service queries */
class ServiceQueryRequestResponse(
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
)(implicit system: ActorSystem[?]) {

  private val requestResponse = new KafkaRequestResponse(
    bootstrapServers = bootstrapServers,
    requestTopic = "service-query-requests",
    responseTopic = "service-query-responses",
    groupId = "service-query-group",
    requestTimeout = 5.seconds
  )

  /** Query player service for player info */
  def queryPlayer(playerId: String): Future[String] = {
    val request = s"""{"service":"player-service","action":"get","id":"$playerId"}"""
    requestResponse.sendRequest(request)
  }

  /** Query analytics service for player stats */
  def queryPlayerStats(playerId: String): Future[String] = {
    val request = s"""{"service":"analytics-service","action":"stats","playerId":"$playerId"}"""
    requestResponse.sendRequest(request)
  }

  /** Start consuming service query responses */
  def startConsumer(): Future[akka.Done] = requestResponse.startResponseConsumer()

  /** Shutdown */
  def shutdown(): Unit = requestResponse.shutdown()
}

object KafkaRequestResponse {
  def apply(
    bootstrapServers: String,
    requestTopic: String,
    responseTopic: String,
    groupId: String,
    requestTimeout: FiniteDuration
  )(implicit system: ActorSystem[?]): KafkaRequestResponse = new KafkaRequestResponse(
    bootstrapServers, requestTopic, responseTopic, groupId, requestTimeout
  )
}

object AiMoveRequestResponse {
  def apply(bootstrapServers: String)(implicit system: ActorSystem[?]): AiMoveRequestResponse =
    new AiMoveRequestResponse(bootstrapServers)
}

object StockfishRequestResponse {
  def apply(bootstrapServers: String)(implicit system: ActorSystem[?]): StockfishRequestResponse =
    new StockfishRequestResponse(bootstrapServers)
}

object ServiceQueryRequestResponse {
  def apply(bootstrapServers: String)(implicit system: ActorSystem[?]): ServiceQueryRequestResponse =
    new ServiceQueryRequestResponse(bootstrapServers)
}
