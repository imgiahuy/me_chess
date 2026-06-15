package api.streams

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Source, Flow, Sink, FileIO, Framing, JsonFraming}
import akka.stream.{IOResult, OverflowStrategy}
import akka.util.ByteString
import controller.GameController
import parser.PgnParser
import service.GameService
import model.PositionState
import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure, Try}

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

  /** Flow: Frame byte strings into PGN game strings.
    *
    * PGN games are separated by blank lines. This flow accumulates
    * lines until a blank line is found, then emits the complete game.
    */
  def pgnFramingFlow: Flow[ByteString, String, akka.NotUsed] = {
    Framing.delimiter(ByteString("\n\n"), maximumFrameLength = 10000, allowTruncation = true)
      .map(_.utf8String.trim)
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

    val stream = fileSource(filePath)
      .via(pgnFramingFlow)
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

    fileSource(filePath)
      .via(pgnFramingFlow)
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
}
