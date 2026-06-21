package tournament.kafka

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import tournament.model._

import java.time.{Duration, Instant}
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue

/** Kafka event publishing for tournament lifecycle events. */
class TournamentKafkaService(bootstrapServers: String):

  private val producer: Option[KafkaProducer[String, String]] =
    if bootstrapServers.nonEmpty && bootstrapServers != "localhost:9092" || sys.env.contains("KAFKA_ENABLED") then
      val props = new Properties()
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
      props.put(ProducerConfig.ACKS_CONFIG, "all")
      props.put(ProducerConfig.RETRIES_CONFIG, "3")
      props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
      try
        Some(new KafkaProducer[String, String](props))
      catch
        case e: Exception =>
          println(s"[WARN] Failed to create Kafka producer: ${e.getMessage}")
          None
    else
      None

  if producer.isDefined then
    println(s"[INFO] Tournament Kafka producer connected to $bootstrapServers")
  else
    println("[INFO] Tournament Kafka events disabled")

  private val pending = new ConcurrentLinkedQueue[ProducerRecord[String, String]]()

  def publishTournamentCreated(tournamentId: String, name: String, format: String): Unit =
    publish("chess-tournament-created", s"""{"tournamentId":"$tournamentId","name":"${escape(name)}","format":"$format","timestamp":"${Instant.now()}"}""")

  def publishParticipantRegistered(tournamentId: String, participantId: String, name: String): Unit =
    publish("chess-tournament-participant-registered", s"""{"tournamentId":"$tournamentId","participantId":"$participantId","name":"${escape(name)}","timestamp":"${Instant.now()}"}""")

  def publishTournamentStarted(tournamentId: String): Unit =
    publish("chess-tournament-started", s"""{"tournamentId":"$tournamentId","timestamp":"${Instant.now()}"}""")

  def publishGameResultReported(tournamentId: String, gameId: String, result: String): Unit =
    publish("chess-tournament-result-reported", s"""{"tournamentId":"$tournamentId","gameId":"$gameId","result":"$result","timestamp":"${Instant.now()}"}""")

  def publishTournamentFinished(tournamentId: String): Unit =
    publish("chess-tournament-finished", s"""{"tournamentId":"$tournamentId","timestamp":"${Instant.now()}"}""")

  private def publish(topic: String, json: String): Unit =
    producer match
      case Some(p) =>
        try
          p.send(new ProducerRecord[String, String](topic, json))
        catch
          case e: Exception =>
            println(s"[WARN] Failed to send Kafka event to $topic: ${e.getMessage}")
      case None =>
        println(s"[INFO] Kafka disabled — would publish to $topic: $json")

  def flush(): Unit =
    producer.foreach(_.flush())

  def shutdown(): Unit =
    producer.foreach { p =>
      p.flush()
      p.close(Duration.ofSeconds(5))
    }

  private def escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

object TournamentKafkaService:
  def fromEnv(): TournamentKafkaService =
    val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val enabled = sys.env.getOrElse("KAFKA_ENABLED", "false").toLowerCase match
      case "true" | "1" | "yes" => true
      case _ => false
    if enabled then new TournamentKafkaService(bootstrapServers)
    else new TournamentKafkaService("")
