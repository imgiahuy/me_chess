package kafka

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Source, Sink, Flow, Broadcast, Merge, GraphDSL, RunnableGraph}
import akka.stream.{Graph, UniformFanOutShape, UniformFanInShape, ClosedShape}
import model.PositionState
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

/** Kafka Integration Service for Chess Microservices.
  *
  * Bridges the REST API game operations with Kafka event streaming,
  * enabling real-time event distribution to microservices.
  *
  * This service:
  * - Publishes game events to Kafka when game state changes
  * - Consumes events from Kafka for reactive processing
  * - Provides stream topologies for event routing and transformation
  * - Integrates with the GameSessionController for bidirectional flow
  */
class KafkaGameEventService(
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
  enablePublishing: Boolean = true,
  enableConsuming: Boolean = true
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  // Producer for publishing game events
  private val producer: ChessKafkaProducer =
    ChessKafkaProducer(bootstrapServers)

  // Consumer for receiving events (optional)
  private val consumer: Option[ChessKafkaConsumer] =
    if (enableConsuming) Some(ChessKafkaConsumer(bootstrapServers)) else None

  private var isRunning = false

  // ========== EVENT PUBLISHING ==========

  /** Publishes a GameCreated event when a new game starts. */
  def publishGameCreated(
    gameId: String,
    whitePlayer: String,
    blackPlayer: String,
    initialState: PositionState
  ): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.gameCreated(
      gameId = gameId,
      whitePlayer = whitePlayer,
      blackPlayer = blackPlayer,
      initialFen = formatter.FenFormatter.fenFormatter(initialState),
      timeControl = initialState.timeControl.map(_.toString)
    )

    producer.publishGameCreated(event).recover {
      case ex => println(s"[KafkaService] Failed to publish game created: ${ex.getMessage}")
    }
  }

  /** Publishes a MoveMade event after a successful move. */
  def publishMoveMade(
    gameId: String,
    state: PositionState,
    moveUCI: String,
    moveNumber: Int
  ): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.moveMade(gameId, state, moveUCI, moveNumber)

    producer.publishMoveMade(event).recover {
      case ex => println(s"[KafkaService] Failed to publish move: ${ex.getMessage}")
    }
  }

  /** Publishes a GameEnded event when game concludes. */
  def publishGameEnded(
    gameId: String,
    state: PositionState,
    pgn: Option[String] = None
  ): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.gameEnded(gameId, state, pgn)

    producer.publishGameEnded(event).recover {
      case ex => println(s"[KafkaService] Failed to publish game ended: ${ex.getMessage}")
    }
  }

  /** Publishes a PlayerResigned event. */
  def publishPlayerResigned(gameId: String, color: String): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.playerResigned(gameId, color)

    producer.publishPlayerResigned(event).recover {
      case ex => println(s"[KafkaService] Failed to publish resignation: ${ex.getMessage}")
    }
  }

  /** Publishes a GameStateUpdated event. */
  def publishGameStateUpdate(gameId: String, state: PositionState): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.gameStateUpdated(gameId, state)

    producer.publishGameStateUpdate(event).recover {
      case ex => println(s"[KafkaService] Failed to publish state update: ${ex.getMessage}")
    }
  }

  /** Generic event publishing - used by API routes. */
  def publishEvent(event: GameEvent, topic: String = GameTopics.GAME_EVENTS): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    producer.publishEvent(event, topic).recover {
      case ex => println(s"[KafkaService] Failed to publish event: ${ex.getMessage}")
    }
  }

  /** Publishes a TimeWarning event. */
  def publishTimeWarning(
    gameId: String,
    playerColor: String,
    remainingTimeMs: Long,
    isTimeout: Boolean = false
  ): Future[Unit] = {
    if (!enablePublishing) return Future.successful(())

    val event = GameEvent.timeWarning(gameId, playerColor, remainingTimeMs, isTimeout)

    producer.publishTimeWarning(event).recover {
      case ex => println(s"[KafkaService] Failed to publish time warning: ${ex.getMessage}")
    }
  }

  // ========== STREAM TOPOLOGIES ==========

  /** Creates a reactive stream that publishes game events.
    *
    * This can be integrated with the GameSessionController for
    * automatic event publishing on state changes.
    */
  def createEventPublishingStream(): Source[GameEvent, akka.actor.ActorRef] = {
    Source.actorRef[GameEvent](
      bufferSize = 100,
      overflowStrategy = akka.stream.OverflowStrategy.dropHead
    )
  }

  /** Creates a broadcast stream topology for routing events to multiple sinks.
    *
    * Useful for sending events to:
    * - Kafka topic
    * - WebSocket clients
    * - Logging/analytics
    * - Database persistence
    */
  def createBroadcastTopology(
    source: Source[GameEvent, _],
    kafkaEnabled: Boolean = true,
    websocketEnabled: Boolean = false,
    analyticsEnabled: Boolean = false
  ): RunnableGraph[akka.NotUsed] = {
    import akka.stream.scaladsl.GraphDSL.Implicits._

    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      val broadcast = builder.add(Broadcast[GameEvent](3))
      val sourceShape = builder.add(source)

      // Branch 1: Kafka publishing
      if (kafkaEnabled) {
        val kafkaSink = builder.add(
          producer.eventSink().contramap[GameEvent](identity)
        )
        broadcast.out(0) ~> kafkaSink
      }

      // Branch 2: WebSocket (placeholder)
      if (websocketEnabled) {
        val wsSink = builder.add(
          Sink.foreach[GameEvent](e => println(s"[WebSocket] $e"))
        )
        broadcast.out(1) ~> wsSink
      }

      // Branch 3: Analytics (placeholder)
      if (analyticsEnabled) {
        val analyticsSink = builder.add(
          Sink.foreach[GameEvent](e => println(s"[Analytics] $e"))
        )
        broadcast.out(2) ~> analyticsSink
      }

      sourceShape ~> broadcast.in

      ClosedShape
    })
  }

  /** Creates a merged stream topology for consuming from multiple sources.
    *
    * Merges events from Kafka with other sources (e.g., internal events).
    */
  def createMergeTopology(
    kafkaSource: Source[GameEvent, _],
    internalSource: Source[GameEvent, _],
    processingSink: Sink[GameEvent, _]
  ): RunnableGraph[akka.NotUsed] = {
    import akka.stream.scaladsl.GraphDSL.Implicits._

    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      val merge = builder.add(Merge[GameEvent](2))
      val kafkaShape = builder.add(kafkaSource)
      val internalShape = builder.add(internalSource)
      val sinkShape = builder.add(processingSink)

      kafkaShape ~> merge.in(0)
      internalShape ~> merge.in(1)
      merge.out ~> sinkShape

      ClosedShape
    })
  }

  // ========== CONSUMER STARTUP ==========

  /** Starts consuming events for reactive processing.
    *
    * @param handler Handler function for consumed events
    */
  def startConsuming(
    handler: GameEvent => Future[Unit] = defaultHandler
  ): Future[akka.Done] = {
    consumer match {
      case Some(c) =>
        isRunning = true
        c.consumeAll(handler = handler).andThen {
          case _ => isRunning = false
        }
      case None =>
        Future.failed(new IllegalStateException("Consuming is disabled"))
    }
  }

  /** Starts a specialized consumer for move events. */
  def startMoveConsumer(
    onMove: MoveMadeEvent => Future[Unit]
  ): Future[akka.Done] = {
    consumer match {
      case Some(c) => c.consumeMoves(onMove)
      case None => Future.failed(new IllegalStateException("Consuming is disabled"))
    }
  }

  /** Starts a consumer for game lifecycle events. */
  def startLifecycleConsumer(
    onCreated: GameCreatedEvent => Future[Unit] = _ => Future.successful(()),
    onEnded: GameEndedEvent => Future[Unit] = _ => Future.successful(())
  ): Future[akka.Done] = {
    consumer match {
      case Some(c) => c.consumeGameLifecycle(onCreated, onEnded)
      case None => Future.failed(new IllegalStateException("Consuming is disabled"))
    }
  }

  /** Default no-op handler. */
  private val defaultHandler: GameEvent => Future[Unit] = _ => Future.successful(())

  // ========== LIFECYCLE ==========

  /** Shuts down the service gracefully. */
  def shutdown(): Future[Unit] = {
    println("[KafkaService] Shutting down...")
    isRunning = false

    val consumerShutdown = consumer.map(_.stopAll()).getOrElse(Future.successful(()))
    val producerShutdown = producer.shutdown()

    Future.sequence(Seq(consumerShutdown, producerShutdown)).map(_ => ())
  }

  def isHealthy: Boolean = isRunning
}

/** Companion object with factory methods. */
object KafkaGameEventService {

  /** Creates a KafkaGameEventService with publishing enabled. */
  def forPublishing(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?]): KafkaGameEventService = {
    new KafkaGameEventService(bootstrapServers, enablePublishing = true, enableConsuming = false)
  }

  /** Creates a KafkaGameEventService with consuming enabled. */
  def forConsuming(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?]): KafkaGameEventService = {
    new KafkaGameEventService(bootstrapServers, enablePublishing = false, enableConsuming = true)
  }

  /** Creates a full KafkaGameEventService with both publishing and consuming. */
  def full(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?]): KafkaGameEventService = {
    new KafkaGameEventService(bootstrapServers, enablePublishing = true, enableConsuming = true)
  }

  /** Creates a disabled/no-op service for testing without Kafka. */
  def disabled(implicit system: ActorSystem[?]): KafkaGameEventService = {
    new KafkaGameEventService("", enablePublishing = false, enableConsuming = false)
  }
}
