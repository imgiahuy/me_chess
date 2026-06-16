package kafka

import org.apache.kafka.common.serialization.{Serializer, Deserializer}
import upickle.default.{ReadWriter, macroRW, write, read}
import java.nio.charset.StandardCharsets
import java.time.Instant

/** JSON Serializer for GameEvent using upickle.
  * Used by Kafka Producer to serialize events to JSON bytes.
  */
class GameEventSerializer extends Serializer[GameEvent] {

  override def serialize(topic: String, data: GameEvent): Array[Byte] = {
    if (data == null) {
      null
    } else {
      val json = GameEventSerializer.toJson(data)
      json.getBytes(StandardCharsets.UTF_8)
    }
  }

  override def close(): Unit = {}
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {}
}

/** JSON Deserializer for GameEvent using upickle.
  * Used by Kafka Consumer to deserialize JSON bytes to events.
  */
class GameEventDeserializer extends Deserializer[GameEvent] {

  override def deserialize(topic: String, data: Array[Byte]): GameEvent = {
    if (data == null) {
      null
    } else {
      val json = new String(data, StandardCharsets.UTF_8)
      GameEventSerializer.fromJson(json)
    }
  }

  override def close(): Unit = {}
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {}
}

/** Companion object with JSON serialization logic for all GameEvent types. */
object GameEventSerializer {

  // Custom ReadWriter for Instant
  given ReadWriter[Instant] = upickle.default.readwriter[String].bimap(
    instant => instant.toString,
    str => Instant.parse(str)
  )

  // ReadWriters for all event types
  given ReadWriter[GameCreatedEvent] = macroRW
  given ReadWriter[MoveMadeEvent] = macroRW
  given ReadWriter[GameEndedEvent] = macroRW
  given ReadWriter[PlayerResignedEvent] = macroRW
  given ReadWriter[DrawOfferedEvent] = macroRW
  given ReadWriter[TimeWarningEvent] = macroRW
  given ReadWriter[GameStateUpdatedEvent] = macroRW

  /** Serializes a GameEvent to JSON string with type discriminator. */
  def toJson(event: GameEvent): String = {
    val baseJson = event match {
      case e: GameCreatedEvent => write(e)
      case e: MoveMadeEvent => write(e)
      case e: GameEndedEvent => write(e)
      case e: PlayerResignedEvent => write(e)
      case e: DrawOfferedEvent => write(e)
      case e: TimeWarningEvent => write(e)
      case e: GameStateUpdatedEvent => write(e)
    }

    // Add eventType field to the JSON for type identification
    val jsonObj = ujson.read(baseJson).obj
    jsonObj("eventType") = ujson.Str(event.eventType)
    jsonObj("_type") = ujson.Str(event.getClass.getSimpleName.replace("$", ""))

    ujson.write(jsonObj)
  }

  /** Deserializes JSON string to appropriate GameEvent subtype. */
  def fromJson(json: String): GameEvent = {
    val jsonObj = ujson.read(json)
    val eventType = jsonObj.obj.get("eventType").map(_.str)
      .orElse(jsonObj.obj.get("_type").map(_.str))
      .getOrElse(throw new IllegalArgumentException(s"Unknown event type in: $json"))

    eventType match {
      case "GAME_CREATED" | "GameCreatedEvent" =>
        read[GameCreatedEvent](json)
      case "MOVE_MADE" | "MoveMadeEvent" =>
        read[MoveMadeEvent](json)
      case "GAME_ENDED" | "GameEndedEvent" =>
        read[GameEndedEvent](json)
      case "PLAYER_RESIGNED" | "PlayerResignedEvent" =>
        read[PlayerResignedEvent](json)
      case "DRAW_OFFERED" | "DrawOfferedEvent" =>
        read[DrawOfferedEvent](json)
      case "TIME_WARNING" | "TimeWarningEvent" | "TIMEOUT" | "Timeout" =>
        read[TimeWarningEvent](json)
      case "GAME_STATE_UPDATED" | "GameStateUpdatedEvent" =>
        read[GameStateUpdatedEvent](json)
      case unknown =>
        throw new IllegalArgumentException(s"Unknown event type: $unknown")
    }
  }
}

/** Simple String serializers for Kafka keys. */
class StringSerializer extends Serializer[String] {
  override def serialize(topic: String, data: String): Array[Byte] =
    if (data == null) null else data.getBytes(StandardCharsets.UTF_8)
  override def close(): Unit = {}
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {}
}

class StringDeserializer extends Deserializer[String] {
  override def deserialize(topic: String, data: Array[Byte]): String =
    if (data == null) null else new String(data, StandardCharsets.UTF_8)
  override def close(): Unit = {}
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = {}
}
