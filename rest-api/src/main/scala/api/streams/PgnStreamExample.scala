package api.streams

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import java.nio.file.{Files, Paths}
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._

/** Example application demonstrating reactive streams for PGN processing.
  *
  * This shows how to:
  * 1. Create a source (file-based PGN data)
  * 2. Apply flows (parse, validate, analyze)
  * 3. Collect in sinks (statistics and console output)
  */
object PgnStreamExample {

  /** Sample PGN games as an external DSL. */
  val samplePgnGames: String =
    """[Event "Famous Chess Game"]
      |[Site "Immortal Game"]
      |[Date "1851.06.21"]
      |[Round "?"]
      |[White "Adolf Anderssen"]
      |[Black "Lionel Kieseritzky"]
      |[Result "1-0"]
      |
      |1. e4 e5 2. f4 exf4 3. Bc4 Qh4+ 4. Kf1 b5 5. Bxb5 Nf6 6. Nf3 Qh6
      |7. d3 Nh5 8. Nh4 Qg5 9. Nf5 c6 10. g4 Nf6 11. Rg1 cxb5 12. h4 Qg6
      |13. h5 Qg5 14. Qf3 Ng8 15. Bxf4 Qf6 16. Nc3 Bc5 17. Nd5 Qxb2
      |18. Bd6 Bxg1 19. e5 Qxa1+ 20. Ke2 Na6 21. Nxg7+ Kd8 22. Qf6+ Nxf6
      |23. Be7# 1-0
      |
      |[Event "World Championship"]
      |[Site "Reykjavik"]
      |[Date "1972.07.11"]
      |[Round "6"]
      |[White "Bobby Fischer"]
      |[Black "Boris Spassky"]
      |[Result "1-0"]
      |
      |1. c4 e6 2. Nf3 d5 3. d4 Nf6 4. Nc3 Be7 5. Bg5 O-O 6. e3 h6
      |7. Bh4 b6 8. cxd5 Nxd5 9. Bxe7 Qxe7 10. Nxd5 exd5 11. Rc1 Be6
      |12. Qa4 c5 13. Qa3 Rc8 14. Bb5 a6 15. dxc5 bxc5 16. O-O Ra7
      |17. Be2 Nd7 18. Nd4 Qf8 19. Nxe6 fxe6 20. e4 d4 21. f4 Qe7
      |22. e5 Rb8 23. Bc4 Kh8 24. Qh3 Nf8 25. b3 a5 26. f5 exf5
      |27. Rxf5 Nh7 28. Rcf1 Qd8 29. Qg3 Re7 30. h4 Rbb7 31. e6 Rbc7
      |32. Qe5 Qe8 33. a4 Qd8 34. R1f2 Qe8 35. R2f3 Qd8 36. Bd3 Qe8
      |37. Qe4 Nf6 38. Rxf6 gxf6 39. Rxf6 Kg8 40. Bc4 Kh8 41. Qf4 1-0
      |
      |[Event "Quick Game"]
      |[Site "Local"]
      |[Date "2024.01.01"]
      |[Round "1"]
      |[White "Player1"]
      |[Black "Player2"]
      |[Result "1/2-1/2"]
      |
      |1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5
      |7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 1/2-1/2
      |""".stripMargin

  def main(args: Array[String]): Unit = {
    println("=" * 60)
    println("CHESS PGN REACTIVE STREAMS EXAMPLE")
    println("=" * 60)
    println()
    println("Demonstrating:")
    println("  - Source: PGN file as reactive stream")
    println("  - Flow 1: Parse PGN (external DSL) to structured data")
    println("  - Flow 2: Validate and reconstruct game states")
    println("  - Flow 3: Analyze game statistics")
    println("  - Sink 1: Collect statistics")
    println("  - Sink 2: Console output")
    println()

    // Create actor system
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "pgn-streams")
    implicit val ec: ExecutionContext = system.executionContext

    try {
      // Create temporary PGN file
      val tempFile = Files.createTempFile("chess_games", ".pgn")
      Files.write(tempFile, samplePgnGames.getBytes)
      println(s"Created temp PGN file: $tempFile")
      println()

      // Run the stream processing
      println("Running reactive stream pipeline...")
      println("-" * 60)

      val startTime = System.currentTimeMillis()
      val statsFuture = PgnProcessingStream.processPgnFile(tempFile)

      // Wait for completion
      val stats = Await.result(statsFuture, 30.seconds)
      val elapsed = System.currentTimeMillis() - startTime

      println("-" * 60)
      println()
      println("STREAM PROCESSING COMPLETE")
      println(s"Processing time: ${elapsed}ms")
      println()
      println("FINAL STATISTICS:")
      println(s"  Total Games Processed: ${stats.totalGames}")
      println(s"  Total Moves: ${stats.totalMoves}")
      println(s"  Checkmates: ${stats.checkmates}")
      println(s"  Stalemates: ${stats.stalemates}")
      println(s"  Games with Castling: ${stats.castlings}")
      println(s"  Games with Promotion: ${stats.promotions}")
      println(s"  Average Moves per Game: ${f"${stats.averageMovesPerGame}%.1f"}")
      if (stats.errors.nonEmpty) {
        println(s"  Errors: ${stats.errors.size}")
        stats.errors.foreach(e => println(s"    - $e"))
      }
      println()

      // Demonstrate additional stream features
      println("Running additional stream demonstrations...")
      println()

      // Demo 2: Throttled stream with filtering
      runThrottledStream(tempFile)

      // Demo 3: In-memory source from iterator
      runInMemoryStream()

      // Demo 4: Custom GraphStage - Sliding Window
      runSlidingWindowDemo(tempFile)

      // Demo 5: Complex GraphDSL topology
      runComplexTopologyDemo(tempFile)

      // Demo 6: Parallel processing with async boundaries
      runParallelProcessingDemo(tempFile)

      // Demo 7: Partitioned stream
      runPartitionedStreamDemo(tempFile)

      // Cleanup
      Files.deleteIfExists(tempFile)
      println()
      println("Cleanup complete. Exiting.")

    } finally {
      system.terminate()
    }
  }

  /** Demonstrates throttled stream processing with filtering. */
  private def runThrottledStream(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 2: Throttled stream with minimum move filter")
    println("-" * 60)

    val shortGamesFuture = PgnProcessingStream.throttledPgnStream(filePath, gamesPerSecond = 5)
      .via(PgnProcessingStream.filterByMinMoves(30)) // Only games with 30+ moves
      .via(PgnProcessingStream.toReportFormat)
      .runWith(Sink.seq)

    val shortGames = Await.result(shortGamesFuture, 30.seconds)
    println(s"Games with 30+ moves: ${shortGames.size}")
    shortGames.foreach(println)
    println()
  }

  /** Demonstrates in-memory stream processing. */
  private def runInMemoryStream()(implicit system: ActorSystem[_]): Unit = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 3: In-memory PGN source")
    println("-" * 60)

    // Create iterator of individual PGN games
    val pgnIterator = Iterator(
      """[Event "Blitz Game 1"]
        |[White "Alice"]
        |[Black "Bob"]
        |[Result "1-0"]
        |
        |1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0
        |""".stripMargin,
      """[Event "Blitz Game 2"]
        |[White "Carol"]
        |[Black "Dave"]
        |[Result "0-1"]
        |
        |1. d4 f5 2. Bg5 h6 3. Bh4 g5 4. Bg3 f4 5. e3 fxg3
        |6. Qh5# 0-1
        |""".stripMargin
    )

    val source = PgnProcessingStream.pgnStringSource(pgnIterator)

    val statsFuture = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.statisticsSink)

    val stats = Await.result(statsFuture, 10.seconds)
    println(s"Processed ${stats.totalGames} games from memory source")
    println(s"Total moves: ${stats.totalMoves}")
    println()
  }

  /** Demonstrates a custom database sink simulation. */
  def runDatabaseSinkDemo(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 4: Database sink simulation")
    println("-" * 60)

    // Simulated database save function
    var savedCount = 0
    val mockSave: PgnProcessingStream.ProcessedGame => Future[Unit] = { game =>
      Future {
        savedCount += 1
        println(s"  [DB] Saved game $savedCount: ${game.tags.getOrElse("Event", "Unknown")}")
        Thread.sleep(100) // Simulate DB latency
      }
    }

    val savedFuture = PgnProcessingStream.fileSourceWithSplitting(filePath)
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.databaseSink(mockSave))

    val count = Await.result(savedFuture, 30.seconds)
    println(s"Total games saved to database: $count")
    println()
  }

  /** Demonstrates custom GraphStage operator - sliding window average. */
  private def runSlidingWindowDemo(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 4: Custom GraphStage - Sliding Window Average")
    println("-" * 60)

    val averagesFuture = PgnProcessingStream.fileSourceWithSplitting(filePath)
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .via(PgnProcessingStream.slidingWindowAverageFlow(windowSize = 2))
      .runWith(Sink.seq)

    val averages = Await.result(averagesFuture, 30.seconds)
    println(s"Running averages (window size 2):")
    averages.zipWithIndex.foreach { case (avg, idx) =>
      println(s"  Game ${idx + 1}: Average moves = ${f"$avg%.1f"}")
    }
    println()
  }

  /** Demonstrates complex GraphDSL topology with broadcast and merge. */
  private def runComplexTopologyDemo(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 5: Complex GraphDSL Topology (Broadcast + Merge)")
    println("-" * 60)

    val resultsFuture = PgnProcessingStream.complexStreamTopology(filePath)
      .runWith(Sink.seq)

    val results = Await.result(resultsFuture, 30.seconds)
    println(s"Complex topology results (${results.size} outputs):")
    results.take(10).foreach(println)
    println()
  }

  /** Demonstrates parallel processing with async boundaries. */
  private def runParallelProcessingDemo(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 6: Parallel Processing with Async Boundaries")
    println("-" * 60)

    val startTime = System.currentTimeMillis()
    val statsFuture = PgnProcessingStream.parallelProcessingStream(filePath)
    val stats = Await.result(statsFuture, 30.seconds)
    val elapsed = System.currentTimeMillis() - startTime

    println(s"Parallel processing completed in ${elapsed}ms")
    println(s"Processed ${stats.totalGames} games with ${stats.totalMoves} total moves")
    println()
  }

  /** Demonstrates partitioned stream based on game length. */
  private def runPartitionedStreamDemo(filePath: java.nio.file.Path)(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext

    println("Demo 7: Partitioned Stream (by game length)")
    println("-" * 60)

    val partitionsFuture = PgnProcessingStream.partitionedStream(filePath)
    val (short, medium, long) = Await.result(partitionsFuture, 30.seconds)

    println(s"Short games (< 10 moves): ${short.size}")
    println(s"Medium games (10-30 moves): ${medium.size}")
    println(s"Long games (> 30 moves): ${long.size}")
    println()
  }
}
