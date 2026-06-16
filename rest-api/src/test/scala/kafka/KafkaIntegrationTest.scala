package kafka

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.Instant

/** Unit tests for Kafka event serialization and basic functionality.
  * 
  * These tests verify the Kafka implementation without requiring
  * a running Kafka server, avoiding version compatibility issues
  * with akka-stream-kafka.
  */
class KafkaIntegrationTest extends AnyWordSpec with Matchers {

  implicit val system: ActorSystem[?] = ActorSystem(Behaviors.empty, "KafkaTestSystem")

  "GameEvent serialization" should {
    "serialize and deserialize GameCreatedEvent correctly" in {
      val serializer = new GameEventSerializer()
      val deserializer = new GameEventDeserializer()

      val originalEvent = GameCreatedEvent(
        gameId = "serialization-test-1",
        whitePlayer = "White",
        blackPlayer = "Black",
        initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        timeControl = Some("blitz+5+3")
      )

      val serialized = serializer.serialize("test-topic", originalEvent)
      serialized should not be null
      serialized should not be empty

      val deserialized = deserializer.deserialize("test-topic", serialized)
      
      deserialized shouldBe a[GameCreatedEvent]
      val gameCreated = deserialized.asInstanceOf[GameCreatedEvent]
      gameCreated.gameId should be (originalEvent.gameId)
      gameCreated.eventType should be (originalEvent.eventType)
      gameCreated.whitePlayer should be (originalEvent.whitePlayer)
      gameCreated.blackPlayer should be (originalEvent.blackPlayer)
    }

    "serialize and deserialize MoveMadeEvent correctly" in {
      val serializer = new GameEventSerializer()
      val deserializer = new GameEventDeserializer()

      val originalEvent = MoveMadeEvent(
        gameId = "move-test-game",
        playerColor = "white",
        moveUCI = "e2e4",
        moveNumber = 1,
        fenAfterMove = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
        remainingWhiteTime = Some(300000L),
        remainingBlackTime = Some(300000L)
      )

      val serialized = serializer.serialize("test-topic", originalEvent)
      serialized should not be null

      val deserialized = deserializer.deserialize("test-topic", serialized)
      
      deserialized shouldBe a[MoveMadeEvent]
      val moveMade = deserialized.asInstanceOf[MoveMadeEvent]
      moveMade.gameId should be (originalEvent.gameId)
      moveMade.moveUCI should be (originalEvent.moveUCI)
      moveMade.moveNumber should be (originalEvent.moveNumber)
    }

    "serialize and deserialize GameEndedEvent correctly" in {
      val serializer = new GameEventSerializer()
      val deserializer = new GameEventDeserializer()

      val originalEvent = GameEndedEvent(
        gameId = "ended-test-game",
        result = "checkmate",
        winner = Some("white"),
        reason = Some("checkmate"),
        finalFen = "rnb1kbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1",
        totalMoves = 15,
        pgn = Some("1. e4 e5 2. Nf3 Nc6")
      )

      val serialized = serializer.serialize("test-topic", originalEvent)
      serialized should not be null

      val deserialized = deserializer.deserialize("test-topic", serialized)
      
      deserialized shouldBe a[GameEndedEvent]
      val gameEnded = deserialized.asInstanceOf[GameEndedEvent]
      gameEnded.gameId should be (originalEvent.gameId)
      gameEnded.result should be (originalEvent.result)
      gameEnded.winner should be (originalEvent.winner)
    }

    "serialize and deserialize TimeWarningEvent correctly" in {
      val serializer = new GameEventSerializer()
      val deserializer = new GameEventDeserializer()

      val originalEvent = TimeWarningEvent(
        gameId = "time-test-game",
        playerColor = "white",
        remainingTimeMs = 30000L,
        isTimeout = false
      )

      val serialized = serializer.serialize("test-topic", originalEvent)
      serialized should not be null

      val deserialized = deserializer.deserialize("test-topic", serialized)
      
      deserialized shouldBe a[TimeWarningEvent]
      val timeWarning = deserialized.asInstanceOf[TimeWarningEvent]
      timeWarning.gameId should be (originalEvent.gameId)
      timeWarning.remainingTimeMs should be (originalEvent.remainingTimeMs)
      timeWarning.isTimeout should be (originalEvent.isTimeout)
    }
  }

  "GameEvent" should {
    "create events with correct types" in {
      val gameCreated = GameEvent.gameCreated(
        gameId = "test-1",
        whitePlayer = "Alice",
        blackPlayer = "Bob",
        initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      )
      gameCreated.eventType should be ("GAME_CREATED")

      val playerResigned = GameEvent.playerResigned("test-2", "white")
      playerResigned.eventType should be ("PLAYER_RESIGNED")
      playerResigned.winner should be ("black")

      val timeWarning = GameEvent.timeWarning("test-3", "black", 10000L, isTimeout = true)
      timeWarning.eventType should be ("TIMEOUT")
    }
  }

  "GameTopics" should {
    "contain all expected topics" in {
      GameTopics.allTopics should contain (GameTopics.GAME_EVENTS)
      GameTopics.allTopics should contain (GameTopics.GAME_CREATED)
      GameTopics.allTopics should contain (GameTopics.MOVE_MADE)
      GameTopics.allTopics should contain (GameTopics.GAME_ENDED)
      GameTopics.allTopics should contain (GameTopics.PLAYER_RESIGNED)
      GameTopics.allTopics should contain (GameTopics.TIME_EVENTS)
      GameTopics.allTopics should contain (GameTopics.GAME_STATE_UPDATES)
    }

    "have 7 topics total" in {
      GameTopics.allTopics should have length 7
    }
  }

  "ChessKafkaProducer" should {
    "create producer settings correctly" in {
      // This test verifies the producer can be instantiated without errors
      // It doesn't require a running Kafka server
      val producer = ChessKafkaProducer("localhost:9092")
      producer should not be null
      producer.shutdown()
    }
  }

  "ChessKafkaConsumer" should {
    "create consumer settings correctly" in {
      // This test verifies the consumer can be instantiated without errors
      // It doesn't require a running Kafka server
      val consumer = ChessKafkaConsumer("localhost:9092")
      consumer should not be null
    }
  }
}
