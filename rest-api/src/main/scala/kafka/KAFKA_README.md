# Kafka Integration for ME Chess

This module provides reactive Kafka streaming for the Chess REST API using Akka Streams (Alpakka Kafka).

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Chess REST API                              │
│                                                                     │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────────────────┐  │
│  │ Game Routes │───▶│ Game Session │───▶│ KafkaGameEventService  │  │
│  └─────────────┘    │ Controller   │    │    (Publisher)         │  │
│                     └──────────────┘    └────────────────────────┘  │
│                                                    │                 │
└────────────────────────────────────────────────────┼────────────────┘
                                                     │
                                                     ▼
                              ┌──────────────────────────────────────┐
                              │        Apache Kafka Cluster          │
                              │                                      │
                              │  ┌─────────────┐  ┌──────────────┐  │
                              │  │ chess-move- │  │ chess-game-  │  │
                              │  │    made     │  │   created    │  │
                              │  └─────────────┘  └──────────────┘  │
                              │  ┌─────────────┐  ┌──────────────┐  │
                              │  │ chess-game- │  │ chess-player-│  │
                              │  │    ended    │  │  resigned    │  │
                              │  └─────────────┘  └──────────────┘  │
                              │  ┌─────────────┐  ┌──────────────┐  │
                              │  │ chess-time- │  │ chess-state- │  │
                              │  │   events    │  │   updates    │  │
                              │  └─────────────┘  └──────────────┘  │
                              └──────────────────────────────────────┘
                                                     │
                              ┌────────────────────┼────────────────────┐
                              │                    │                    │
                              ▼                    ▼                    ▼
                    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
                    │  Move Analyzer  │  │   Game Stats    │  │  Notification   │
                    │   Microservice  │  │   Microservice  │  │  Microservice   │
                    │   (Consumer)    │  │   (Consumer)    │  │   (Consumer)    │
                    └─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Features

### 1. Reactive Producer (ChessKafkaProducer)
- **Akka Streams Integration**: Backpressure-aware publishing
- **Event Types**: GameCreated, MoveMade, GameEnded, PlayerResigned, TimeWarning, GameStateUpdated
- **Partitioning**: Events partitioned by `gameId` for ordered processing per game
- **Exactly-Once Semantics**: Idempotent producer with acknowledgment config

### 2. Reactive Consumer (ChessKafkaConsumer)
- **Automatic Reconnection**: Exponential backoff on failures
- **Manual Commit**: At-least-once delivery with manual offset management
- **Typed Consumers**: Filter and handle specific event types
- **Partitioned Processing**: Parallel processing with per-game ordering

### 3. Integration Service (KafkaGameEventService)
- **Bidirectional Flow**: Publishes game events and consumes from microservices
- **Stream Topologies**: Broadcast and merge topologies for event routing
- **Microservice Integration**: Connects REST API with event-driven architecture

## Quick Start

### 1. Start Kafka Infrastructure

```bash
# Start Zookeeper, Kafka, and Kafka UI
docker-compose -f docker-compose.kafka.yml up -d

# Verify topics were created
docker exec chess-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 2. Run the REST API with Kafka

```bash
# Set environment variable and run
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
sbt "restApi/run"
```

### 3. Test Event Publishing

```bash
# Create a game (publishes GameCreated event)
curl -X POST http://localhost:8080/v1/chess/games \
  -H "Content-Type: application/json" \
  -d '{"whitePlayer":"Alice","blackPlayer":"Bob","timeControl":"blitz"}'

# Make a move (publishes MoveMade event)
curl -X POST http://localhost:8080/v1/chess/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"from":"e2","to":"e4"}'
```

### 4. Monitor Events in Kafka UI

Open http://localhost:8081 and navigate to the Topics section to see real-time events.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `KAFKA_ENABLE_PUBLISHING` | `true` | Enable event publishing |
| `KAFKA_ENABLE_CONSUMING` | `false` | Enable event consuming |
| `KAFKA_CONSUMER_GROUP` | `chess-consumers` | Consumer group ID |

### Producer Settings

```scala
// Key settings in ChessKafkaProducer
.withProperty("acks", "all")                    // Wait for all replicas
.withProperty("retries", "3")                   // Retry on transient failures
.withProperty("enable.idempotence", "true")     // Exactly-once semantics
.withProperty("compression.type", "snappy")     // Compress messages
```

### Consumer Settings

```scala
// Key settings in ChessKafkaConsumer
.withProperty("auto.offset.reset", "latest")    // Start from latest
.withProperty("enable.auto.commit", "false")    // Manual commit for reliability
.withProperty("max.poll.records", "500")        // Batch size
```

## Usage Examples

### Publishing Events from Game Controller

```scala
class GameSessionController(
  controller: GameController,
  kafkaService: KafkaGameEventService
) {
  
  def createGame(white: String, black: String): String = {
    val gameId = controller.createGame(white, black)
    val state = controller.getState(gameId)
    
    // Publish to Kafka
    kafkaService.publishGameCreated(gameId, white, black, state)
    
    gameId
  }
  
  def makeMove(gameId: String, uci: String): Either[String, PositionState] = {
    controller.makeMove(gameId, uci) match {
      case Right(newState) =>
        val moveNumber = newState.moveHistory.length
        
        // Publish move event to Kafka
        kafkaService.publishMoveMade(gameId, newState, uci, moveNumber)
        
        // Check if game ended
        if (newState.gameResult != Ongoing) {
          kafkaService.publishGameEnded(gameId, newState)
        }
        
        Right(newState)
      case Left(error) => Left(error)
    }
  }
}
```

### Consuming Events in a Microservice

```scala
// Analytics microservice
val consumer = ChessKafkaConsumer.forGroup("analytics-service")

// Consume only move events
consumer.consumeMoves { moveEvent =>
  Future {
    println(s"Move ${moveEvent.moveNumber}: ${moveEvent.moveUCI}")
    // Store in analytics database
    analyticsDb.recordMove(moveEvent)
  }
}

// Consume game lifecycle
consumer.consumeGameLifecycle(
  onCreated = { event =>
    Future { analyticsDb.incrementActiveGames() }
  },
  onEnded = { event =>
    Future { 
      analyticsDb.decrementActiveGames()
      analyticsDb.recordGameResult(event.result, event.winner)
    }
  }
)
```

### Stream Processing with Akka Streams

```scala
import akka.stream.scaladsl.{Source, Sink, Flow}

// Create a processing pipeline
val processingFlow = Flow[GameEvent]
  .collect { case m: MoveMadeEvent => m }
  .filter(_.moveNumber > 10)  // Only late-game moves
  .mapAsync(4) { event =>
    // Enrich with position analysis
    analyzePosition(event.fenAfterMove)
  }
  .to(Sink.foreach(println))

// Connect to Kafka source
val source = Consumer.plainSource(consumerSettings, Subscriptions.topics("chess-move-made"))
source.via(processingFlow).run()
```

## Kafka Topics

| Topic | Partitions | Description |
|-------|------------|-------------|
| `chess-events` | 4 | General game events |
| `chess-game-created` | 4 | New game creation |
| `chess-move-made` | 8 | Move events (higher throughput) |
| `chess-game-ended` | 4 | Game conclusion |
| `chess-player-resigned` | 4 | Resignation events |
| `chess-time-events` | 4 | Time warnings/timeouts |
| `chess-state-updates` | 4 | State change notifications |

## Testing

### Unit Tests with Embedded Kafka

```scala
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

class KafkaSpec extends AnyWordSpec with EmbeddedKafka {
  "ChessKafkaProducer" should {
    "publish and consume events" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)
      
      withRunningKafka {
        val producer = ChessKafkaProducer("localhost:9092")
        val consumer = ChessKafkaConsumer("localhost:9092", "test-group")
        
        val event = GameCreatedEvent(gameId = "123", whitePlayer = "Alice", blackPlayer = "Bob", initialFen = "...")
        
        // Publish
        producer.publishGameCreated(event).futureValue
        
        // Consume
        val received = consumer.eventSource().take(1).runWith(Sink.head).futureValue
        received.value() shouldBe event
      }
    }
  }
}
```

## REST API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/v1/kafka/health` | GET | Kafka connection status |
| `/v1/kafka/publish` | POST | Publish custom event |
| `/v1/kafka/stream/{topic}` | GET | SSE stream of events |
| `/v1/kafka/topics` | GET | List all topics |
| `/v1/kafka/topics/{name}` | GET | Topic details |

## Troubleshooting

### Connection Issues

```bash
# Check if Kafka is running
docker ps | grep kafka

# Test connectivity
nc -zv localhost 9092

# View Kafka logs
docker logs chess-kafka
```

### Consumer Lag

```bash
# Check consumer group lag
docker exec chess-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group chess-consumers
```

### Reset Offsets

```bash
# Reset to latest (skip old messages)
docker exec chess-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group chess-consumers \
  --reset-offsets \
  --to-latest \
  --execute \
  --topic chess-move-made
```

## Integration with Microservices

### WebSocket Broadcasting Service
```scala
// Consumes Kafka events and broadcasts to WebSocket clients
class WebSocketBroadcaster(kafkaConsumer: ChessKafkaConsumer) {
  def start(): Unit = {
    kafkaConsumer.consumeAll { event =>
      Future {
        webSocketClients.filter(_.gameId == event.gameId).foreach { client =>
          client.send(Json.stringify(event))
        }
      }
    }
  }
}
```

### AI Analysis Service
```scala
// Analyzes positions from move events
class AnalysisService(kafkaConsumer: ChessKafkaConsumer, 
                      kafkaProducer: ChessKafkaProducer) {
  def start(): Unit = {
    kafkaConsumer.consumeMoves { moveEvent =>
      Future {
        val analysis = chessEngine.analyze(moveEvent.fenAfterMove)
        val enrichedEvent = moveEvent.copy(analysis = Some(analysis))
        
        // Publish enriched event
        kafkaProducer.publishEvent(enrichedEvent)
      }
    }
  }
}
```

## Performance Tuning

### Producer Throughput
- Increase `batch.size` for higher throughput (trade-off: latency)
- Use `linger.ms` to wait for batch fills
- Enable compression (`snappy` or `lz4`)

### Consumer Throughput
- Increase `max.poll.records` for larger batches
- Use `mapAsync` with appropriate parallelism
- Consider separate consumer groups for different processing speeds

### Partition Strategy
- More partitions = higher parallelism
- Keep related events on same partition (use `gameId` as key)
- Balance partition count with consumer instances

## References

- [Alpakka Kafka Documentation](https://doc.akka.io/docs/alpakka-kafka/current/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Akka Streams Documentation](https://doc.akka.io/docs/akka/current/stream/)
