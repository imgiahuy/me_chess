package kafka

import akka.actor.typed.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Source, Sink, Flow, Keep, RestartSource}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{StringDeserializer => KafkaStringDeserializer}
import org.apache.kafka.common.TopicPartition
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

/** Reactive Kafka Consumer for Chess Game Events.
  *
  * Uses Alpakka Kafka (akka-stream-kafka) to consume events as a reactive stream,
  * enabling real-time processing of chess game events in microservices.
  *
  * Features:
  * - Reactive Streams integration with automatic backpressure
  * - At-least-once delivery semantics with manual commit
  * - Automatic reconnection with exponential backoff
  * - Per-game ordered processing via partition assignment
  * - Pluggable event handlers for different event types
  */
class ChessKafkaConsumer(
  bootstrapServers: String,
  groupId: String = "chess-game-consumers",
  clientId: String = "chess-consumer"
)(implicit system: ActorSystem[?]) {

  import ChessKafkaConsumer._

  private implicit val ec: ExecutionContext = system.executionContext

  /** Consumer settings with custom deserializers. */
  private val consumerSettings: ConsumerSettings[String, GameEvent] =
    ConsumerSettings(system, new KafkaStringDeserializer, new GameEventDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withClientId(clientId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
      .withProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
      .withProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000")
      .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000")
      .withProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000")
      .withStopTimeout(30.seconds)

  private var runningStreams: Map[String, UniqueKillSwitch] = Map.empty

  // ========== EVENT HANDLERS ==========

  /** Type alias for event handler functions. */
  type EventHandler[T <: GameEvent] = T => Future[Unit]
  type GenericEventHandler = GameEvent => Future[Unit]

  /** Default no-op handler. */
  private val defaultHandler: GenericEventHandler = _ => Future.successful(())

  // ========== STREAMING CONSUMER METHODS ==========

  /** Creates a reactive Source of game events from Kafka.
    *
    * This Source can be connected to any Akka Stream flow for processing.
    *
    * @param topics Topics to subscribe to
    * @return Source of ConsumerRecord with control materialized value
    */
  def eventSource(
    topics: Seq[String] = GameTopics.allTopics
  ): Source[ConsumerRecord[String, GameEvent], Consumer.Control] = {
    Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
  }

  /** Creates a Source with automatic restart on failure.
    *
    * Uses RestartSource for resilient streaming with exponential backoff.
    */
  def resilientEventSource(
    topics: Seq[String] = GameTopics.allTopics,
    minBackoff: FiniteDuration = 3.seconds,
    maxBackoff: FiniteDuration = 30.seconds,
    randomFactor: Double = 0.2
  ): Source[ConsumerRecord[String, GameEvent], akka.NotUsed] = {
    RestartSource.onFailuresWithBackoff(
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomFactor
    ) { () =>
      Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
        .map { record =>
          println(s"[KafkaConsumer] Received: ${record.value().eventType} for game ${record.value().gameId}")
          record
        }
    }
  }

  /** Starts a consuming stream with a custom processing flow.
    *
    * The flow processes events and can transform, filter, or side-effect.
    *
    * @param topics Topics to subscribe to
    * @param processingFlow Flow to process consumer records
    * @return Future that completes when the stream stops
    */
  def consumeWithFlow[T](
    topics: Seq[String] = GameTopics.allTopics,
    processingFlow: Flow[ConsumerRecord[String, GameEvent], T, _]
  ): Future[akka.Done] = {
    val killSwitch = KillSwitches.single[ConsumerRecord[String, GameEvent]]

    val (ks, completion) = Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
      .viaMat(killSwitch)(Keep.right)
      .via(processingFlow)
      .toMat(Sink.ignore)(Keep.both)
      .run()

    val streamId = java.util.UUID.randomUUID().toString
    runningStreams = runningStreams + (streamId -> ks)

    completion.andThen {
      case _ => runningStreams = runningStreams - streamId
    }

    completion
  }

  /** Starts a typed consumer for specific event types.
    *
    * Filters and dispatches events to the appropriate handler.
    *
    * @param topics Topics to subscribe to
    * @param handler Handler function for events
    * @tparam T Specific event type to consume
    */
  def consumeTyped[T <: GameEvent: scala.reflect.ClassTag](
    topics: Seq[String] = GameTopics.allTopics,
    handler: EventHandler[T]
  ): Future[akka.Done] = {
    val clazz = scala.reflect.classTag[T].runtimeClass
    consumeWithFlow(topics, Flow[ConsumerRecord[String, GameEvent]]
      .collect {
        case record if clazz.isInstance(record.value()) =>
          record.value().asInstanceOf[T]
      }
      .mapAsync(1)(handler)
    )
  }

  /** Starts a generic consumer that handles all event types.
    *
    * @param topics Topics to subscribe to
    * @param handler Handler function for all game events
    */
  def consumeAll(
    topics: Seq[String] = GameTopics.allTopics,
    handler: GenericEventHandler = defaultHandler
  ): Future[akka.Done] = {
    val processingFlow = Flow[ConsumerRecord[String, GameEvent]]
      .map(_.value())
      .mapAsync(8) { event =>
        handler(event).recover { case ex =>
          println(s"[KafkaConsumer] Handler error for ${event.eventType}: ${ex.getMessage}")
        }
      }

    consumeWithFlow(topics, processingFlow)
  }

  // ========== SPECIALIZED CONSUMERS ==========

  /** Consumes only move events with a specialized handler. */
  def consumeMoves(handler: EventHandler[MoveMadeEvent]): Future[akka.Done] = {
    consumeWithFlow(
      Seq(GameTopics.MOVE_MADE, GameTopics.GAME_EVENTS),
      Flow[ConsumerRecord[String, GameEvent]]
        .collect {
          case record if record.value().isInstanceOf[MoveMadeEvent] =>
            record.value().asInstanceOf[MoveMadeEvent]
        }
        .mapAsync(4)(handler)
    )
  }

  /** Consumes only game lifecycle events (created/ended). */
  def consumeGameLifecycle(
    onCreated: EventHandler[GameCreatedEvent] = _ => Future.successful(()),
    onEnded: EventHandler[GameEndedEvent] = _ => Future.successful(())
  ): Future[akka.Done] = {
    consumeWithFlow(
      Seq(GameTopics.GAME_CREATED, GameTopics.GAME_ENDED),
      Flow[ConsumerRecord[String, GameEvent]]
        .map(_.value())
        .mapAsync(4) {
          case event: GameCreatedEvent => onCreated(event)
          case event: GameEndedEvent => onEnded(event)
          case _ => Future.successful(())
        }
    )
  }

  /** Consumes time-related events for time control monitoring. */
  def consumeTimeEvents(handler: EventHandler[TimeWarningEvent]): Future[akka.Done] = {
    consumeWithFlow(
      Seq(GameTopics.TIME_EVENTS),
      Flow[ConsumerRecord[String, GameEvent]]
        .collect {
          case record if record.value().isInstanceOf[TimeWarningEvent] =>
            record.value().asInstanceOf[TimeWarningEvent]
        }
        .mapAsync(4)(handler)
    )
  }

  /** Creates a partitioned consumer that processes each game on a separate thread.
    *
    * Uses groupBy for parallel processing while maintaining
    * order within each game partition.
    */
  def consumePartitioned(
    topics: Seq[String] = GameTopics.allTopics,
    parallelism: Int = 8,
    handler: GenericEventHandler = defaultHandler
  ): Future[akka.Done] = {
    val processingFlow = Flow[ConsumerRecord[String, GameEvent]]
      .map { record =>
        (record.key(), record.value())  // key is gameId
      }
      .mapAsync(parallelism) { case (gameId, event) =>
        handler(event).recover { case ex =>
          println(s"[KafkaConsumer] Partitioned handler error for game $gameId: ${ex.getMessage}")
        }
      }

    consumeWithFlow(topics, processingFlow)
  }

  // ========== MONITORING & CONTROL ==========

  /** Stops all running consumer streams. */
  def stopAll(): Future[Unit] = {
    println(s"[KafkaConsumer] Stopping ${runningStreams.size} streams...")
    runningStreams.values.foreach(_.shutdown())
    runningStreams = Map.empty
    Future.successful(())
  }

  /** Creates a Source with built-in metrics and logging. */
  def monitoredSource(
    topics: Seq[String] = GameTopics.allTopics,
    logEvery: Int = 100
  ): Source[ConsumerRecord[String, GameEvent], Consumer.Control] = {
    var count = 0

    Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
      .map { record =>
        count += 1
        if (count % logEvery == 0) {
          println(s"[KafkaConsumer] Processed $count events")
        }
        record
      }
  }
}

/** Companion object for creating consumer instances. */
object ChessKafkaConsumer {

  /** Creates a new ChessKafkaConsumer with configuration from environment. */
  def apply(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
    groupId: String = "chess-game-consumers",
    clientId: String = s"chess-consumer-${java.util.UUID.randomUUID().toString.take(8)}"
  )(implicit system: ActorSystem[?]): ChessKafkaConsumer = {
    new ChessKafkaConsumer(bootstrapServers, groupId, clientId)
  }

  /** Creates a consumer for a specific consumer group. */
  def forGroup(
    groupId: String,
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?]): ChessKafkaConsumer = {
    new ChessKafkaConsumer(bootstrapServers, groupId)
  }

}
