# Reactive Streams Implementation

This project demonstrates **reactive streams** using **Akka Streams** to process chess game data in the form of PGN (Portable Game Notation) files.

## Overview

The implementation follows the reactive streams pattern with:
- **Source**: PGN file input (external DSL)
- **Flows**: Parse → Validate → Analyze
- **Sinks**: Statistics collection and database storage

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌─────────────┐
│   Source    │────▶│  Flow 1      │────▶│  Flow 2      │────▶│  Flow 3      │────▶│    Sink     │
│  FileIO     │     │ PGN Framing  │     │ PGN Parsing  │     │  Analysis    │     │ Statistics  │
│ (ByteString)│     │ (to String)  │     │ (to Domain)  │     │ (compute)    │     │ (aggregate) │
└─────────────┘     └──────────────┘     └──────────────┘     └──────────────┘     └─────────────┘
```

## Components

### Source

**File Source** (`fileSource`): Reads PGN files as `ByteString` chunks using Akka's `FileIO`.

```scala
def fileSource(filePath: Path): Source[ByteString, Future[IOResult]]
```

**In-Memory Source** (`pgnStringSource`): For testing, creates a source from an iterator of PGN strings.

### Flows

1. **PGN Framing Flow** (`pgnFramingFlow`): Frames byte strings into individual PGN game strings using `\n\n` as delimiter.

2. **PGN Parsing Flow** (`pgnParsingFlow`): Parses PGN text (external DSL) into structured game data using the existing `PgnParser`.

3. **Game Analysis Flow** (`gameAnalysisFlow`): Analyzes parsed games to compute:
   - Move count
   - Checkmate detection
   - Castling detection
   - Promotion detection

4. **Logging Flow** (`loggingFlow`): Side-effect flow for monitoring processing progress.

5. **Filtering Flow** (`filterByMinMoves`): Filters games by minimum move count.

6. **Report Flow** (`toReportFormat`): Transforms games to string reports.

### Sinks

1. **Statistics Sink** (`statisticsSink`): Aggregates all processed games into `GameStatistics`.

2. **Database Sink** (`databaseSink`): Simulates async database writes with backpressure.

## Usage

### Run the Example

```bash
sbt "rest-api/runMain api.streams.PgnStreamExample"
```

This will:
1. Create a temporary PGN file with famous chess games
2. Process the file through the reactive stream pipeline
3. Display statistics (total games, moves, checkmates, etc.)
4. Run additional demos (throttled stream, in-memory processing)

### Run Tests

```bash
sbt rest-api/test
```

### Use in Your Code

```scala
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import api.streams.PgnProcessingStream
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration._

implicit val system = ActorSystem(Behaviors.empty[Unit], "chess-streams")

// Process a PGN file
val stats = Await.result(
  PgnProcessingStream.processPgnFile(Paths.get("games.pgn")),
  30.seconds
)

println(s"Processed ${stats.totalGames} games")
```

## External DSL: PGN Format

PGN (Portable Game Notation) is the external DSL used as input:

```pgn
[Event "World Championship"]
[Site "Reykjavik"]
[Date "1972.07.11"]
[White "Bobby Fischer"]
[Black "Boris Spassky"]
[Result "1-0"]

1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 *
```

The parser converts this textual DSL to domain objects (`Move`, `PositionState`).

## Backpressure & Throttling

The stream supports backpressure and throttling:

```scala
// Process max 10 games per second
val throttled = PgnProcessingStream
  .throttledPgnStream(filePath, gamesPerSecond = 10)
  .runWith(sink)
```

## Future: Kafka Integration

In the next lectures, the source will be replaced with a **Kafka consumer**:

```scala
// Future: Kafka source instead of file source
val kafkaSource: Source[ByteString, _] = 
  Consumer.plainSource(consumerSettings, Subscriptions.topics("chess-games"))
```

## File Locations

- **Stream Implementation**: `rest-api/src/main/scala/api/streams/PgnProcessingStream.scala`
- **Example Runner**: `rest-api/src/main/scala/api/streams/PgnStreamExample.scala`
- **Tests**: `rest-api/src/test/scala/streams/PgnProcessingStreamTest.scala`
- **Sample Data**: `sample_games.pgn`

## Dependencies

The implementation uses:
- `akka-stream` 2.8.5 (already in `build.sbt`)
- `akka-actor-typed` 2.8.5
- Existing `core` module for PGN parsing and game logic
