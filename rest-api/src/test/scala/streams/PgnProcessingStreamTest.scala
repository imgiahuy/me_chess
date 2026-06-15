package streams

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import api.streams.PgnProcessingStream
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{ExecutionContext, Future}

class PgnProcessingStreamTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem[Unit] = _
  implicit var ec: ExecutionContext = _

  override def beforeAll(): Unit = {
    system = ActorSystem(Behaviors.empty[Unit], "test-system")
    ec = system.executionContext
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "PgnProcessingStream" should "parse PGN games from in-memory source" in {
    val pgn1 =
      """[Event "Test Game 1"]
        |[White "PlayerA"]
        |[Black "PlayerB"]
        |
        |1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *
        |""".stripMargin

    val pgn2 =
      """[Event "Test Game 2"]
        |[White "PlayerC"]
        |[Black "PlayerD"]
        |
        |1. d4 d5 2. c4 e6 3. Nc3 Nf6 *
        |""".stripMargin

    val source = PgnProcessingStream.pgnStringSource(Iterator(pgn1, pgn2))

    val futureStats = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.statisticsSink)

    futureStats.map { stats =>
      stats.totalGames shouldBe 2
      stats.totalMoves shouldBe 10 // 5 moves per game
      stats.errors shouldBe empty
    }
  }

  it should "handle parsing errors gracefully" in {
    val invalidPgn = "This is not valid PGN"
    val validPgn =
      """[Event "Valid Game"]
        |[White "A"]
        |[Black "B"]
        |
        |1. e4 e5 2. Nf3 Nc6 *
        |""".stripMargin

    val source = PgnProcessingStream.pgnStringSource(Iterator(invalidPgn, validPgn))

    val futureStats = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.statisticsSink)

    futureStats.map { stats =>
      stats.totalGames shouldBe 2
      stats.errors should have size 1
    }
  }

  it should "filter games by minimum moves" in {
    val shortGame =
      """[Event "Short"]
        |[White "A"]
        |[Black "B"]
        |
        |1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0
        |""".stripMargin

    val longGame =
      """[Event "Long"]
        |[White "C"]
        |[Black "D"]
        |
        |1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7
        |6. Re1 b5 7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 *
        |""".stripMargin

    val source = PgnProcessingStream.pgnStringSource(Iterator(shortGame, longGame))

    val futureGames = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .via(PgnProcessingStream.filterByMinMoves(10))
      .runWith(akka.stream.scaladsl.Sink.seq)

    futureGames.map { games =>
      games should have size 1
      games.head.tags.get("Event") shouldBe Some("Long")
    }
  }

  it should "detect checkmates correctly" in {
    val checkmateGame =
      """[Event "Scholar's Mate"]
        |[White "Beginner"]
        |[Black "Victim"]
        |[Result "1-0"]
        |
        |1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0
        |""".stripMargin

    val source = PgnProcessingStream.pgnStringSource(Iterator(checkmateGame))

    val futureStats = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.statisticsSink)

    futureStats.map { stats =>
      stats.totalGames shouldBe 1
      stats.checkmates shouldBe 1
    }
  }

  it should "transform games to report format" in {
    val game =
      """[Event "Test Report"]
        |[White "X"]
        |[Black "Y"]
        |
        |1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *
        |""".stripMargin

    val source = PgnProcessingStream.pgnStringSource(Iterator(game))

    val futureReports = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .via(PgnProcessingStream.toReportFormat)
      .runWith(akka.stream.scaladsl.Sink.seq)

    futureReports.map { reports =>
      reports should have size 1
      reports.head should include("Test Report")
      reports.head should include("Moves: 6")
    }
  }

  it should "support async database sink" in {
    val game =
      """[Event "DB Test"]
        |[White "X"]
        |[Black "Y"]
        |
        |1. e4 e5 2. Nf3 Nc6 *
        |""".stripMargin

    var savedGames = List.empty[String]
    val mockSave: PgnProcessingStream.ProcessedGame => Future[Unit] = { g =>
      Future.successful {
        savedGames = g.tags.getOrElse("Event", "Unknown") :: savedGames
      }
    }

    val source = PgnProcessingStream.pgnStringSource(Iterator(game))

    val futureCount = source
      .via(PgnProcessingStream.pgnParsingFlow)
      .via(PgnProcessingStream.gameAnalysisFlow)
      .runWith(PgnProcessingStream.databaseSink(mockSave))

    futureCount.map { count =>
      count shouldBe 1
      savedGames should contain("DB Test")
    }
  }
}
