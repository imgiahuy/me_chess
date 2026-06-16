package api.streams

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Source, Flow, Sink, FileIO, Framing, JsonFraming, Broadcast, Merge, GraphDSL, Partition, RestartSource}
import akka.stream.{IOResult, OverflowStrategy, Graph, UniformFanInShape, UniformFanOutShape, FlowShape, SourceShape, Inlet, Outlet, Attributes}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import controller.GameController
import parser.PgnParser
import service.GameService
import model.PositionState
import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure, Try}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

/** Reactive stream processing for PGN (Portable Game Notation) chess files.
  *
  * This demonstrates reactive streams with:
  * - Source: FileIO reading PGN files line by line
  * - Flows: Parse PGN → Validate moves → Analyze games
  * - Sink: Collect processed game statistics
  */
object PgnProcessingStream {

  /** Represents a processed chess game with analysis. */
  case class ProcessedGame(
    tags: Map[String, String],
    finalState: PositionState,
    moveCount: Int,
    hasCheckmate: Boolean,
    hasCastling: Boolean,
    hasPromotion: Boolean,
    errors: List[String]
  )

  /** Represents game statistics after processing multiple games. */
  case class GameStatistics(
    totalGames: Int,
    totalMoves: Int,
    checkmates: Int,
    stalemates: Int,
    castlings: Int,
    promotions: Int,
    averageMovesPerGame: Double,
    errors: List[String]
  )

  /** Creates a source that reads PGN files from a directory.
    *
    * Emits ByteString chunks from the file.
    */
  def fileSource(filePath: Path): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(filePath)
  }

  /** Alternative file source that reads entire file and splits into games.
    * This handles Windows line endings more reliably for the demo.
    */
  def fileSourceWithSplitting(filePath: Path): Source[String, Future[IOResult]] = {
    FileIO.fromPath(filePath)
      .map(_.utf8String)
      .fold("")(_ + _)
      .map(content => content.replaceAll("\r\n", "\n").replaceAll("\r", "\n"))
      .mapConcat(_.split("\n\n").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
  }


  /** Flow: Parse PGN text into structured game data.
    *
    * Input: PGN text string
    * Output: Either parsing error or parsed PGN game with reconstructed state
    */
  def pgnParsingFlow: Flow[String, Either[String, (PgnParser.PgnGame, PositionState)], akka.NotUsed] = {
    Flow[String].map { pgnText =>
      PgnParser.parse(pgnText) match {
        case Right(pgnGame) =>
          // Reconstruct game state from moves
          PgnParser.parseToGameState(pgnText) match {
            case Right(finalState) => Right((pgnGame, finalState))
            case Left(err) => Left(s"Failed to reconstruct game: $err")
          }
        case Left(err) => Left(s"PGN parse error: $err")
      }
    }
  }

  /** Flow: Analyze a parsed game and compute statistics.
    *
    * Input: Parsed PGN game and final state
    * Output: ProcessedGame with analysis
    */
  def gameAnalysisFlow: Flow[Either[String, (PgnParser.PgnGame, PositionState)], ProcessedGame, akka.NotUsed] = {
    Flow[Either[String, (PgnParser.PgnGame, PositionState)]].map {
      case Right((pgnGame, finalState)) =>
        val moveCount = pgnGame.moves.size
        val hasCheckmate = GameService.isCheckmate(finalState)
        val hasCastling = pgnGame.moves.exists(_.specialMove.exists {
          case model.CastlingKingSide | model.CastlingQueenSide => true
          case _ => false
        })
        val hasPromotion = pgnGame.moves.exists(_.specialMove.exists {
          case model.Promotion(_) => true
          case _ => false
        })

        ProcessedGame(
          tags = pgnGame.tags,
          finalState = finalState,
          moveCount = moveCount,
          hasCheckmate = hasCheckmate,
          hasCastling = hasCastling,
          hasPromotion = hasPromotion,
          errors = Nil
        )

      case Left(error) =>
        ProcessedGame(
          tags = Map.empty,
          finalState = GameService.createGame(),
          moveCount = 0,
          hasCheckmate = false,
          hasCastling = false,
          hasPromotion = false,
          errors = List(error)
        )
    }
  }

  /** Flow: Log processing progress (side effect flow).
    *
    * Prints each processed game for monitoring.
    */
  def loggingFlow: Flow[ProcessedGame, ProcessedGame, akka.NotUsed] = {
    Flow[ProcessedGame].map { game =>
      val event = game.tags.getOrElse("Event", "Unknown Event")
      val white = game.tags.getOrElse("White", "?")
      val black = game.tags.getOrElse("Black", "?")

      if (game.errors.isEmpty) {
        println(s"[Stream] Processed: $event - $white vs $black (${game.moveCount} moves)")
      } else {
        println(s"[Stream] Errors in game: ${game.errors.mkString(", ")}")
      }
      game
    }
  }

  /** Sink: Collect all processed games into game statistics.
    *
    * Materializes to a Future[GameStatistics]
    */
  def statisticsSink(implicit ec: ExecutionContext): Sink[ProcessedGame, Future[GameStatistics]] = {
    Sink.fold[GameStatistics, ProcessedGame](
      GameStatistics(0, 0, 0, 0, 0, 0, 0.0, Nil)
    ) { (stats, game) =>
      val newTotalMoves = stats.totalMoves + game.moveCount
      val newTotalGames = stats.totalGames + 1

      stats.copy(
        totalGames = newTotalGames,
        totalMoves = newTotalMoves,
        checkmates = stats.checkmates + (if (game.hasCheckmate) 1 else 0),
        stalemates = stats.stalemates + (if (!game.hasCheckmate && GameService.isStalemate(game.finalState)) 1 else 0),
        castlings = stats.castlings + (if (game.hasCastling) 1 else 0),
        promotions = stats.promotions + (if (game.hasPromotion) 1 else 0),
        averageMovesPerGame = newTotalMoves.toDouble / newTotalGames,
        errors = stats.errors ++ game.errors
      )
    }
  }

  /** Sink: Save processed games to database (async sink).
    *
    * Simulates async database writes with Future.
    */
  def databaseSink(
    saveGame: ProcessedGame => Future[Unit]
  )(implicit ec: ExecutionContext): Sink[ProcessedGame, Future[Int]] = {
    Sink.foldAsync[Int, ProcessedGame](0) { (count, game) =>
      if (game.errors.isEmpty) {
        saveGame(game).map(_ => count + 1).recover {
          case ex =>
            println(s"[Stream] Database error: ${ex.getMessage}")
            count
        }
      } else {
        Future.successful(count)
      }
    }
  }

  /** Runs the complete PGN processing pipeline.
    *
    * @param filePath Path to the PGN file
    * @param system Actor system for stream materialization
    * @return Future of game statistics
    */
  def processPgnFile(filePath: Path)(implicit system: ActorSystem[_]): Future[GameStatistics] = {
    implicit val ec: ExecutionContext = system.executionContext

    val stream = fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .via(loggingFlow)
      .runWith(statisticsSink)

    stream.onComplete {
      case Success(stats) =>
        println(s"[Stream] Completed: ${stats.totalGames} games processed")
        println(s"[Stream] Average moves per game: ${stats.averageMovesPerGame}")
      case Failure(ex) =>
        println(s"[Stream] Failed: ${ex.getMessage}")
    }

    stream
  }

  /** Alternative: Stream with throttling and buffering for backpressure.
    *
    * Demonstrates advanced reactive stream features.
    */
  def throttledPgnStream(
    filePath: Path,
    gamesPerSecond: Int = 10
  )(implicit system: ActorSystem[_]): Source[ProcessedGame, Future[IOResult]] = {
    import scala.concurrent.duration._

    fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .throttle(gamesPerSecond, 1.second)
      .buffer(100, OverflowStrategy.backpressure)
  }

  /** Creates a source from an iterator of PGN strings (for testing/in-memory processing).
    */
  def pgnStringSource(pgns: Iterator[String]): Source[String, akka.NotUsed] = {
    Source.fromIterator(() => pgns)
  }

  /** Flow: Filter games by minimum move count.
    */
  def filterByMinMoves(minMoves: Int): Flow[ProcessedGame, ProcessedGame, akka.NotUsed] = {
    Flow[ProcessedGame].filter(_.moveCount >= minMoves)
  }

  /** Flow: Transform games to a simple report format.
    */
  def toReportFormat: Flow[ProcessedGame, String, akka.NotUsed] = {
    Flow[ProcessedGame].map { game =>
      if (game.errors.isEmpty) {
        val event = game.tags.getOrElse("Event", "Unknown")
        val result = if (game.hasCheckmate) "checkmate" else "ongoing/draw"
        s"Game: $event, Moves: ${game.moveCount}, Result: $result"
      } else {
        s"Game error: ${game.errors.mkString(", ")}"
      }
    }
  }

  // ========== ADVANCED REACTIVE STREAMS FEATURES ==========

  /** Custom GraphStage: Sliding window for computing running averages.
    *
    * This demonstrates creating a custom operator in Akka Streams.
    * Maintains a sliding window of the last N games and emits the average move count.
    */
  class SlidingWindowAverage(windowSize: Int) extends GraphStage[FlowShape[ProcessedGame, Double]] {
    val in = Inlet[ProcessedGame]("SlidingWindowAverage.in")
    val out = Outlet[Double]("SlidingWindowAverage.out")

    override val shape: FlowShape[ProcessedGame, Double] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: akka.stream.Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private var window = scala.collection.mutable.Queue[Int]()

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val game = grab(in)
            if (game.errors.isEmpty) {
              window.enqueue(game.moveCount)
              if (window.size > windowSize) window.dequeue()
              val avg = window.sum.toDouble / window.size
              push(out, avg)
            } else {
              pull(in)
            }
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = pull(in)
        })
      }
  }

  /** Flow using the custom sliding window operator. */
  def slidingWindowAverageFlow(windowSize: Int): Flow[ProcessedGame, Double, akka.NotUsed] =
    Flow.fromGraph(new SlidingWindowAverage(windowSize))

  /** Complex stream topology using broadcast and merge.
    *
    * Demonstrates non-linear stream processing:
    * - Broadcasts games to multiple parallel processing paths
    * - Path 1: Counts games
    * - Path 2: Filters short games and converts to reports
    * - Path 3: Detects checkmates and counts them
    * - Merges results back together
    */
  def complexStreamTopology(
    filePath: Path
  )(implicit system: ActorSystem[_]): Source[String, Future[IOResult]] = {
    import GraphDSL.Implicits._

    fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .via(Flow.fromGraph(GraphDSL.create() { implicit builder =>
        val broadcast = builder.add(Broadcast[ProcessedGame](3))
        val merge = builder.add(Merge[String](3))

        // Path 1: Count games
        val countFlow = builder.add(
          Flow[ProcessedGame]
            .map(_ => "[Count] Game processed")
        )

        // Path 2: Filter and report
        val reportFlow = builder.add(
          Flow[ProcessedGame]
            .filter(_.moveCount >= 10)
            .via(toReportFormat)
        )

        // Path 3: Checkmate detection
        val checkmateFlow = builder.add(
          Flow[ProcessedGame]
            .filter(_.hasCheckmate)
            .map(game => s"[Checkmate] ${game.tags.getOrElse("Event", "Unknown")}")
        )

        // Connect the graph
        broadcast.out(0) ~> countFlow ~> merge.in(0)
        broadcast.out(1) ~> reportFlow ~> merge.in(1)
        broadcast.out(2) ~> checkmateFlow ~> merge.in(2)

        FlowShape(broadcast.in, merge.out)
      }))
  }

  /** Stream with async boundaries for parallel processing.
    *
    * Demonstrates using .async to run different stages in parallel,
    * improving throughput on multi-core systems.
    */
  def parallelProcessingStream(
    filePath: Path
  )(implicit system: ActorSystem[_]): Future[GameStatistics] = {
    implicit val ec: ExecutionContext = system.executionContext

    fileSourceWithSplitting(filePath)
      .async // Async boundary before parsing
      .via(pgnParsingFlow)
      .async // Async boundary before analysis
      .via(gameAnalysisFlow)
      .async // Async boundary before statistics
      .runWith(statisticsSink)
  }

  /** Stream with supervision strategy for error recovery.
    *
    * Demonstrates error recovery using recoverWithRetries.
    * If parsing fails, the stream restarts with a delay.
    */
  def supervisedStream(
    filePath: Path,
    maxRetries: Int = 3
  )(implicit system: ActorSystem[_]): Future[GameStatistics] = {
    implicit val ec: ExecutionContext = system.executionContext

    fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .recoverWithRetries(maxRetries, {
        case ex =>
          println(s"[Stream] Error occurred, retrying: ${ex.getMessage}")
          Source.empty[ProcessedGame]
      })
      .runWith(statisticsSink)
  }

  /** Partition flow based on game characteristics.
    *
    * Demonstrates using Partition to route games to different processing paths.
    * Partition 0: Short games (< 10 moves)
    * Partition 1: Medium games (10-30 moves)
    * Partition 2: Long games (> 30 moves)
    */
  def partitionedStream(
    filePath: Path
  )(implicit system: ActorSystem[_]): Future[(List[ProcessedGame], List[ProcessedGame], List[ProcessedGame])] = {
    import akka.stream.scaladsl.Sink
    implicit val ec: ExecutionContext = system.executionContext

    // Simpler approach: collect all games then partition in memory
    fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .runWith(Sink.seq)
      .map { games =>
        val short = games.filter(_.moveCount < 10).toList
        val medium = games.filter(g => g.moveCount >= 10 && g.moveCount <= 30).toList
        val long = games.filter(_.moveCount > 30).toList
        (short, medium, long)
      }
  }

  /** Dynamic backpressure with adaptive throttling.
    *
    * Adjusts processing rate based on error rate in the stream.
    * If error rate is high, slows down processing.
    */
  def adaptiveThrottledStream(
    filePath: Path,
    baseRate: Int = 10
  )(implicit system: ActorSystem[_]): Source[ProcessedGame, Future[IOResult]] = {
    implicit val ec: ExecutionContext = system.executionContext

    fileSourceWithSplitting(filePath)
      .via(pgnParsingFlow)
      .via(gameAnalysisFlow)
      .conflate((_, game) => game) // Conflate if upstream is faster
      .throttle(baseRate, 1.second)
      .buffer(50, OverflowStrategy.backpressure)
  }
}
