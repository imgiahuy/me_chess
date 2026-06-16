package kafka

import akka.actor.typed.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{Source, Flow, Sink, Keep}
import org.apache.kafka.clients.producer.{ProducerRecord, Producer => JavaKafkaProducer}
import org.apache.kafka.common.serialization.{StringSerializer => KafkaStringSerializer}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}

/** Reactive Kafka Producer for Chess Game Events.
  *
  * Uses Alpakka Kafka (akka-stream-kafka) to integrate with Akka Streams,
  * providing backpressure-aware, non-blocking event publishing.
  *
  * Features:
  * - Reactive Streams integration with backpressure
  * - Batch publishing for high throughput
  * - Error handling with retry logic
  * - Topic partitioning by gameId for ordered processing per game
  */
class ChessKafkaProducer(
  bootstrapServers: String,
  defaultTopic: String = "chess-events"
)(implicit system: ActorSystem[?]) {

  import ChessKafkaProducer._

  private implicit val ec: ExecutionContext = system.executionContext

  /** Producer settings with custom serializers. */
  private val producerSettings: ProducerSettings[String, GameEvent] =
    ProducerSettings(system, new KafkaStringSerializer, new GameEventSerializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty("acks", "all")                    // Wait for all replicas
      .withProperty("retries", "3")                   // Retry on transient failures
      .withProperty("batch.size", "16384")            // Batch size in bytes
      .withProperty("linger.ms", "5")                 // Wait up to 5ms to batch
      .withProperty("compression.type", "none")       // Disable compression for Alpine compatibility
      .withProperty("max.in.flight.requests", "5")  // Allow parallel sends
      .withProperty("enable.idempotence", "true")     // Exactly-once semantics

  /** Internal Java producer for synchronous operations. */
  private val javaProducer: JavaKafkaProducer[String, GameEvent] =
    producerSettings.createKafkaProducer()

  /** Gracefully shuts down the producer. */
  def shutdown(): Future[Unit] = Future {
    javaProducer.close()
  }

  // ========== STREAMING PRODUCER METHODS ==========

  /** Publishes a single event to Kafka asynchronously.
    *
    * @param event The game event to publish
    * @param topic Optional topic override (defaults to "chess-events")
    * @return Future completing when the event is sent
    */
  def publishEvent(event: GameEvent, topic: String = defaultTopic): Future[Unit] = {
    val record = new ProducerRecord[String, GameEvent](
      topic,
      event.gameId,  // Partition key for game-ordered processing
      event
    )

    val promise = Promise[Unit]()

    javaProducer.send(record, (metadata, exception) => {
      if (exception != null) {
        promise.failure(exception)
      } else {
        promise.success(())
      }
    })

    promise.future
  }

  /** Creates a reactive Sink that publishes events to Kafka.
    *
    * This Sink can be connected to any Akka Stream Source to create
    * a reactive data pipeline.
    *
    * @param topic The Kafka topic to publish to
    * @return Sink accepting GameEvent, materializing to Future[Done]
    */
  def eventSink(topic: String = defaultTopic): Sink[GameEvent, Future[akka.Done]] = {
    Producer.plainSink(producerSettings)
      .contramap[GameEvent] { event =>
        new ProducerRecord[String, GameEvent](topic, event.gameId, event)
      }
  }

  /** Creates a Flow that transforms events and publishes to Kafka.
    *
    * Allows inline publishing within a stream processing pipeline.
    *
    * @param topic The Kafka topic to publish to
    * @return Flow from GameEvent to Unit (simplified)
    */
  def eventPublishFlow(topic: String = defaultTopic): Flow[GameEvent, Unit, akka.NotUsed] = {
    import akka.kafka.ProducerMessage
    Flow[GameEvent]
      .map { event =>
        val record = new ProducerRecord[String, GameEvent](topic, event.gameId, event)
        ProducerMessage.single(record, ())
      }
      .via(Producer.flexiFlow(producerSettings))
      .map(_ => ())
  }

  /** Publishes a batch of events efficiently.
    *
    * Uses Akka Streams for batch processing with backpressure.
    *
    * @param events Sequence of events to publish
    * @param topic The Kafka topic
    * @return Future with list of successfully published events
    */
  def publishBatch(events: Seq[GameEvent], topic: String = defaultTopic): Future[Seq[GameEvent]] = {
    if (events.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      Source(events.toList)
        .via(eventPublishFlow(topic))
        .map(_ => true)  // Track success
        .runWith(Sink.seq)
        .map(results => events.take(results.count(identity)))
    }
  }

  // ========== GAME-SPECIFIC PUBLISHING METHODS ==========

  /** Publishes game creation event. */
  def publishGameCreated(event: GameCreatedEvent): Future[Unit] =
    publishEvent(event, GameTopics.GAME_CREATED)

  /** Publishes move made event. */
  def publishMoveMade(event: MoveMadeEvent): Future[Unit] =
    publishEvent(event, GameTopics.MOVE_MADE)

  /** Publishes game ended event. */
  def publishGameEnded(event: GameEndedEvent): Future[Unit] =
    publishEvent(event, GameTopics.GAME_ENDED)

  /** Publishes player resigned event. */
  def publishPlayerResigned(event: PlayerResignedEvent): Future[Unit] =
    publishEvent(event, GameTopics.PLAYER_RESIGNED)

  /** Publishes time warning event. */
  def publishTimeWarning(event: TimeWarningEvent): Future[Unit] =
    publishEvent(event, GameTopics.TIME_EVENTS)

  /** Publishes game state update event. */
  def publishGameStateUpdate(event: GameStateUpdatedEvent): Future[Unit] =
    publishEvent(event, GameTopics.GAME_STATE_UPDATES)

  // ========== STREAMING SOURCE INTEGRATION ==========

  /** Integrates with a Source of game events for continuous publishing.
    *
    * Creates a complete stream from source to Kafka with proper
    * backpressure and error handling.
    *
    * @param source The source of game events
    * @param topic The target Kafka topic
    * @return Future completing when the stream finishes
    */
  def publishFromSource(
    source: Source[GameEvent, _],
    topic: String = defaultTopic
  ): Future[akka.Done] = {
    source
      .via(eventPublishFlow(topic))
      .runWith(Sink.ignore)
  }

  /** Creates a monitored publishing stream with progress logging.
    *
    * Logs publishing metrics every N events.
    */
  def monitoredPublishStream(
    source: Source[GameEvent, _],
    topic: String = defaultTopic,
    logEvery: Int = 100
  ): Future[akka.Done] = {
    var count = 0

    source
      .map { event =>
        count += 1
        if (count % logEvery == 0) {
          println(s"[KafkaProducer] Published $count events to $topic")
        }
        event
      }
      .via(eventPublishFlow(topic))
      .runWith(Sink.ignore)
      .andThen {
        case Success(_) => println(s"[KafkaProducer] Stream completed. Total: $count events")
        case Failure(ex) => println(s"[KafkaProducer] Stream failed: ${ex.getMessage}")
      }
  }
}

/** Kafka topic constants for chess game events. */
object GameTopics {
  val GAME_EVENTS = "chess-events"
  val GAME_CREATED = "chess-game-created"
  val MOVE_MADE = "chess-move-made"
  val GAME_ENDED = "chess-game-ended"
  val PLAYER_RESIGNED = "chess-player-resigned"
  val TIME_EVENTS = "chess-time-events"
  val GAME_STATE_UPDATES = "chess-state-updates"

  /** All topic names as a sequence. */
  val allTopics: Seq[String] = Seq(
    GAME_EVENTS,
    GAME_CREATED,
    MOVE_MADE,
    GAME_ENDED,
    PLAYER_RESIGNED,
    TIME_EVENTS,
    GAME_STATE_UPDATES
  )
}

/** Companion object for creating producer instances. */
object ChessKafkaProducer {

  /** Creates a new ChessKafkaProducer with configuration from environment. */
  def apply(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    defaultTopic: String = "chess-events"
  )(implicit system: ActorSystem[?]): ChessKafkaProducer = {
    new ChessKafkaProducer(bootstrapServers, defaultTopic)
  }

  /** Creates a producer with custom settings. */
  def withSettings(
    bootstrapServers: String,
    settings: ProducerSettings[String, GameEvent]
  )(implicit system: ActorSystem[?]): ChessKafkaProducer = {
    new ChessKafkaProducer(bootstrapServers)
  }
}
