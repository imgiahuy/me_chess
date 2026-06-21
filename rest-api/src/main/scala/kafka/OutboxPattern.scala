package kafka

import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.{Producer, Consumer}
import akka.kafka.{ProducerSettings, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{Sink, Source}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{StringSerializer, StringDeserializer}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.util.UUID
import java.time.Instant
import upickle.default._

/** Outbox pattern for reliable event publishing.
  * 
  * This pattern ensures reliable event publishing by:
  * 1. Storing events in an outbox table (database) first
  * 2. Having a separate process read from outbox and publish to Kafka
  * 3. Deleting events from outbox only after successful Kafka publication
  * 4. Providing retry logic for failed publications
  * 
  * Benefits:
  * - Exactly-once semantics (with idempotent consumers)
  * - No lost events even if Kafka is temporarily unavailable
  * - Transactional consistency with business operations
  * - Audit trail of all events
  */
case class OutboxEvent(
  id: String,
  eventType: String,
  payload: String,
  topic: String,
  partition: Option[Int] = None,
  key: Option[String] = None,
  status: String = "pending",  // pending, published, failed
  createdAt: Instant = Instant.now(),
  publishedAt: Option[Instant] = None,
  failureCount: Int = 0,
  lastError: Option[String] = None
)

/** Outbox repository interface */
trait OutboxRepository {
  def save(event: OutboxEvent)(implicit ec: ExecutionContext): Future[Unit]
  def markAsPublished(id: String)(implicit ec: ExecutionContext): Future[Unit]
  def markAsFailed(id: String, error: String)(implicit ec: ExecutionContext): Future[Unit]
  def getPendingEvents(limit: Int = 100)(implicit ec: ExecutionContext): Future[Seq[OutboxEvent]]
  def deletePublishedEvents(olderThan: Instant)(implicit ec: ExecutionContext): Future[Int]
  def getFailedEvents(limit: Int = 100)(implicit ec: ExecutionContext): Future[Seq[OutboxEvent]]
}

/** In-memory outbox repository (for testing) */
class InMemoryOutboxRepository extends OutboxRepository {
  import scala.collection.concurrent.TrieMap

  private val events = TrieMap.empty[String, OutboxEvent]

  override def save(event: OutboxEvent)(implicit ec: ExecutionContext): Future[Unit] = Future {
    events.put(event.id, event)
  }

  override def markAsPublished(id: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
    events.replace(id, events.get(id).map { event =>
      event.copy(
        status = "published",
        publishedAt = Some(Instant.now())
      )
    }.orNull)
  }

  override def markAsFailed(id: String, error: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
    events.replace(id, events.get(id).map { event =>
      event.copy(
        status = "failed",
        failureCount = event.failureCount + 1,
        lastError = Some(error)
      )
    }.orNull)
  }

  override def getPendingEvents(limit: Int = 100)(implicit ec: ExecutionContext): Future[Seq[OutboxEvent]] = Future {
    events.values.filter(_.status == "pending").take(limit).toSeq
  }

  override def deletePublishedEvents(olderThan: Instant)(implicit ec: ExecutionContext): Future[Int] = Future {
    val toDelete = events.collect {
      case (id, event) if event.status == "published" && 
                         event.publishedAt.exists(_.isBefore(olderThan)) => id
    }.toSeq
    toDelete.foreach(events.remove)
    toDelete.length
  }

  override def getFailedEvents(limit: Int = 100)(implicit ec: ExecutionContext): Future[Seq[OutboxEvent]] = Future {
    events.values.filter(_.status == "failed").take(limit).toSeq
  }
}

/** Outbox publisher that reads from outbox and publishes to Kafka */
class OutboxPublisher(
  repository: OutboxRepository,
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
  maxRetries: Int = 3,
  retryDelay: FiniteDuration = 5.seconds
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  // Store cancellables for periodic tasks
  private var periodicTasks: List[akka.actor.Cancellable] = List.empty

  // Producer settings
  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)
    .withProperty("acks", "all")
    .withProperty("enable.idempotence", "true")
    .withProperty("retries", maxRetries.toString)

  /** Publish a single event to Kafka */
  private def publishEvent(event: OutboxEvent): Future[Unit] = {
    val record = new ProducerRecord[String, String](
      event.topic,
      event.partition.getOrElse(0),
      event.key.orNull,
      event.payload
    )

    Source.single(record)
      .runWith(Producer.plainSink(producerSettings))
      .map(_ => ())
  }

  /** Process pending events from outbox */
  def processPendingEvents(): Future[Int] = {
    repository.getPendingEvents(100).flatMap { events =>
      println(s"[OutboxPublisher] Processing ${events.length} pending events")

      Future.sequence(
        events.map { event =>
          publishEvent(event)
            .flatMap { _ =>
              println(s"[OutboxPublisher] Published event ${event.id} to ${event.topic}")
              repository.markAsPublished(event.id)
            }
            .recoverWith { case ex =>
              println(s"[OutboxPublisher] Failed to publish event ${event.id}: ${ex.getMessage}")
              repository.markAsFailed(event.id, ex.getMessage)
            }
        }
      ).map(_ => events.length)
    }
  }

  /** Retry failed events */
  def retryFailedEvents(): Future[Int] = {
    repository.getFailedEvents(100).flatMap { events =>
      println(s"[OutboxPublisher] Retrying ${events.length} failed events")

      Future.sequence(
        events.filter(_.failureCount < maxRetries).map { event =>
          publishEvent(event)
            .flatMap { metadata =>
              println(s"[OutboxPublisher] Successfully retried event ${event.id}")
              repository.markAsPublished(event.id)
            }
            .recoverWith { case ex =>
              println(s"[OutboxPublisher] Retry failed for event ${event.id}: ${ex.getMessage}")
              repository.markAsFailed(event.id, ex.getMessage)
            }
        }
      ).map(_ => events.length)
    }
  }

  /** Cleanup old published events */
  def cleanupOldPublishedEvents(retentionPeriod: FiniteDuration = 24.hours): Future[Int] = {
    val cutoff = Instant.now().minusMillis(retentionPeriod.toMillis)
    repository.deletePublishedEvents(cutoff).map { count =>
      println(s"[OutboxPublisher] Cleaned up $count old published events")
      count
    }
  }

  /** Start periodic processing */
  def startPeriodicProcessing(
    interval: FiniteDuration = 10.seconds,
    cleanupInterval: FiniteDuration = 1.hour
  ): Future[Unit] = {
    println(s"[OutboxPublisher] Starting periodic processing every $interval")

    // Process pending events periodically
    val pendingProcessor = system.scheduler.scheduleAtFixedRate(
      initialDelay = 5.seconds,
      interval = interval
    ) { () =>
      processPendingEvents().recover {
        case ex => println(s"[OutboxPublisher] Error processing pending events: ${ex.getMessage}")
      }
    }

    // Retry failed events periodically
    val failedProcessor = system.scheduler.scheduleAtFixedRate(
      initialDelay = 30.seconds,
      interval = interval * 2
    ) { () =>
      retryFailedEvents().recover {
        case ex => println(s"[OutboxPublisher] Error retrying failed events: ${ex.getMessage}")
      }
    }

    // Cleanup old events periodically
    val cleanupProcessor = system.scheduler.scheduleAtFixedRate(
      initialDelay = 1.hour,
      interval = cleanupInterval
    ) { () =>
      cleanupOldPublishedEvents().recover {
        case ex => println(s"[OutboxPublisher] Error during cleanup: ${ex.getMessage}")
      }
    }

    // Store cancellables for later cancellation
    periodicTasks = List(pendingProcessor, failedProcessor, cleanupProcessor)

    Future.successful(())
  }

  /** Stop periodic processing */
  def stopPeriodicProcessing(): Unit = {
    println("[OutboxPublisher] Stopping periodic processing")
    periodicTasks.foreach(_.cancel())
    periodicTasks = List.empty
  }
}

/** Outbox service for adding events to outbox */
class OutboxService(
  repository: OutboxRepository
)(implicit ec: ExecutionContext) {

  /** Add an event to the outbox */
  def addEvent(
    eventType: String,
    payload: String,
    topic: String,
    partition: Option[Int] = None,
    key: Option[String] = None
  ): Future[String] = {
    val event = OutboxEvent(
      id = UUID.randomUUID().toString,
      eventType = eventType,
      payload = payload,
      topic = topic,
      partition = partition,
      key = key
    )
    repository.save(event).map(_ => event.id)
  }

  /** Add a typed event to the outbox */
  def addTypedEvent[T](
    event: T,
    eventType: String,
    topic: String,
    partition: Option[Int] = None,
    key: Option[String] = None
  )(implicit writer: upickle.default.Writer[T]): Future[String] = {
    val payload = write(event)
    addEvent(eventType, payload, topic, partition, key)
  }
}

/** Integration with existing KafkaGameEventService */
class OutboxKafkaGameEventService(
  outboxService: OutboxService,
  bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
)(implicit system: ActorSystem[?]) {

  private implicit val ec: ExecutionContext = system.executionContext

  /** Publish game created event via outbox */
  def publishGameCreated(
    gameId: String,
    whitePlayer: String,
    blackPlayer: String,
    initialFen: String,
    timeControl: Option[String]
  ): Future[String] = {
    val payload = write(ujson.Obj(
      "gameId" -> gameId,
      "whitePlayer" -> whitePlayer,
      "blackPlayer" -> blackPlayer,
      "initialFen" -> initialFen,
      "timeControl" -> timeControl
    ))
    outboxService.addEvent("GameCreated", payload, "chess-game-created", key = Some(gameId))
  }

  /** Publish move made event via outbox */
  def publishMoveMade(
    gameId: String,
    moveUCI: String,
    moveNumber: Int,
    fen: String
  ): Future[String] = {
    val payload = write(ujson.Obj(
      "gameId" -> gameId,
      "moveUCI" -> moveUCI,
      "moveNumber" -> moveNumber,
      "fen" -> fen
    ))
    outboxService.addEvent("MoveMade", payload, "chess-move-made", key = Some(gameId))
  }

  /** Publish game ended event via outbox */
  def publishGameEnded(
    gameId: String,
    result: String,
    winner: Option[String],
    pgn: Option[String]
  ): Future[String] = {
    val payload = write(ujson.Obj(
      "gameId" -> gameId,
      "result" -> result,
      "winner" -> winner,
      "pgn" -> pgn
    ))
    outboxService.addEvent("GameEnded", payload, "chess-game-ended", key = Some(gameId))
  }

  /** Publish player resigned event via outbox */
  def publishPlayerResigned(
    gameId: String,
    color: String
  ): Future[String] = {
    val payload = write(ujson.Obj(
      "gameId" -> gameId,
      "color" -> color
    ))
    outboxService.addEvent("PlayerResigned", payload, "chess-player-resigned", key = Some(gameId))
  }
}

object OutboxPattern {
  /** Create a complete outbox setup with in-memory repository */
  def createInMemory(
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?], ec: ExecutionContext): (OutboxService, OutboxPublisher) = {
    val repository = new InMemoryOutboxRepository()
    val outboxService = new OutboxService(repository)
    val publisher = new OutboxPublisher(repository, bootstrapServers)
    (outboxService, publisher)
  }

  /** Create outbox setup with custom repository */
  def create(
    repository: OutboxRepository,
    bootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  )(implicit system: ActorSystem[?], ec: ExecutionContext): (OutboxService, OutboxPublisher) = {
    val outboxService = new OutboxService(repository)
    val publisher = new OutboxPublisher(repository, bootstrapServers)
    (outboxService, publisher)
  }
}
