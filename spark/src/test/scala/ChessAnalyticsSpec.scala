import analytics.{ChessAnalytics, GameData}
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ChessAnalyticsSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private var spark: SparkSession = _
  private var analytics: ChessAnalytics = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .appName("ChessAnalyticsTest")
      .master("local[*]")
      .config("spark.sql.adaptive.enabled", "false")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    analytics = ChessAnalytics.create(spark)
  }

  override def afterAll(): Unit = {
    if (spark != null) spark.stop()
  }

  private def sampleGames: Seq[GameData] = Seq(
    GameData("1", "Alice",   "Bob",     "1-0",     Some("White"), 45, java.sql.Date.valueOf("2024-01-01"), Some("blitz"),     Some(180000L), Some(120000L)),
    GameData("2", "Bob",     "Alice",   "0-1",     Some("Black"), 67, java.sql.Date.valueOf("2024-01-02"), Some("blitz"),     Some(240000L), Some(180000L)),
    GameData("3", "Alice",   "Charlie", "1/2-1/2", None,          89, java.sql.Date.valueOf("2024-01-03"), Some("rapid"),     Some(600000L), Some(540000L)),
    GameData("4", "Charlie", "Alice",   "1-0",     Some("White"), 34, java.sql.Date.valueOf("2024-01-04"), Some("rapid"),     Some(480000L), Some(420000L)),
    GameData("5", "Bob",     "Charlie", "0-1",     Some("Black"), 56, java.sql.Date.valueOf("2024-01-05"), Some("classical"), Some(1800000L),Some(1740000L))
  )

  test("countVictories resolves color to player name") {
    import spark.implicits._
    val df = spark.createDataFrame(sampleGames)
    val victories = analytics.countVictories(df)

    victories.count() should be > 0L

    val aliceWins   = victories.filter($"player" === "Alice").collect()
    val charlieWins = victories.filter($"player" === "Charlie").collect()

    aliceWins.length   should be(1)
    charlieWins.length should be(1)

    aliceWins.head.getAs[Long]("victories")   should be(2L)
    charlieWins.head.getAs[Long]("victories") should be(1L)
  }

  test("countVictories orders by victories descending") {
    import spark.implicits._
    val df     = spark.createDataFrame(sampleGames)
    val result = analytics.countVictories(df).collect()

    result.head.getAs[Long]("victories") should be >= result.last.getAs[Long]("victories")
  }

  test("findHighscorePlayer returns exactly one row with Most Victories achievement") {
    import spark.implicits._
    val df        = spark.createDataFrame(sampleGames)
    val highscore = analytics.findHighscorePlayer(df)

    highscore.count() should be(1L)
    highscore.collect().head.getAs[String]("achievement") should be("Most Victories")
    highscore.collect().head.getAs[String]("player") should be("Alice")
  }

  test("getPlayerStatistics computes correct totals for Alice") {
    import spark.implicits._
    val df    = spark.createDataFrame(sampleGames)
    val stats = analytics.getPlayerStatistics(df)

    val alice = stats.filter($"player" === "Alice").collect().head
    alice.getAs[Long]("totalGames") should be(4L)
    alice.getAs[Long]("victories")  should be(2L)
    alice.getAs[Long]("defeats")    should be(1L)
    alice.getAs[Long]("draws")      should be(1L)
  }

  test("getPlayerStatistics winRate is victories / totalGames") {
    import spark.implicits._
    val df    = spark.createDataFrame(sampleGames)
    val stats = analytics.getPlayerStatistics(df)

    val alice = stats.filter($"player" === "Alice").collect().head
    val wr    = alice.getAs[Double]("winRate")
    wr should be(0.5 +- 0.001)
  }

  test("findBestPlayerByMetric winRate returns Alice") {
    import spark.implicits._
    val df   = spark.createDataFrame(sampleGames)
    val best = analytics.findBestPlayerByMetric(df, "winRate")

    best.count() should be(1L)
    best.collect().head.getAs[String]("player") should be("Alice")
  }

  test("findBestPlayerByMetric victories returns Alice") {
    import spark.implicits._
    val df   = spark.createDataFrame(sampleGames)
    val best = analytics.findBestPlayerByMetric(df, "victories")

    best.count() should be(1L)
    best.collect().head.getAs[String]("player") should be("Alice")
  }

  test("findBestPlayerByMetric totalGames returns Alice (most games played)") {
    import spark.implicits._
    val df   = spark.createDataFrame(sampleGames)
    val best = analytics.findBestPlayerByMetric(df, "totalGames")

    best.count() should be(1L)
    best.collect().head.getAs[String]("player") should be("Alice")
  }

  test("analyzeGameResults counts each result type correctly") {
    import spark.implicits._
    val df      = spark.createDataFrame(sampleGames)
    val results = analytics.analyzeGameResults(df)

    results.count() should be(3L)

    val whiteWins = results.filter($"result" === "1-0").collect().head.getAs[Long]("count")
    val blackWins = results.filter($"result" === "0-1").collect().head.getAs[Long]("count")
    val draws     = results.filter($"result" === "1/2-1/2").collect().head.getAs[Long]("count")

    whiteWins should be(2L)
    blackWins should be(2L)
    draws     should be(1L)
  }

  test("analyzeGameResults percentages sum to 100") {
    import spark.implicits._
    val df         = spark.createDataFrame(sampleGames)
    val results    = analytics.analyzeGameResults(df)
    val totalPct   = results.agg(sum("percentage")).collect().head.getAs[Double](0)

    totalPct should be(100.0 +- 0.1)
  }

  test("analyzeTimeControls groups by time control") {
    import spark.implicits._
    val df       = spark.createDataFrame(sampleGames)
    val tcResult = analytics.analyzeTimeControls(df)

    tcResult.count() should be(3L)

    val blitz = tcResult.filter($"timeControl" === "blitz").collect().head
    blitz.getAs[Long]("gamesPlayed") should be(2L)
  }

  test("readPgnFile parses sample_games.pgn") {
    val path = "sample_games.pgn"
    val df   = analytics.readPgnFile(path)
    df.count() should be(4L)

    import spark.implicits._
    val whites = df.select("whitePlayer").as[String].collect().toSet
    whites should contain("Adolf Anderssen")
    whites should contain("Bobby Fischer")
    whites should contain("Paul Morphy")
    whites should contain("Alice")
  }

  test("batch sample data produces non-empty analytics") {
    val sampleDF = ChessSparkApp.createSampleGames(analytics)
    sampleDF.count() should be(10L)

    analytics.countVictories(sampleDF).count()          should be > 0L
    analytics.findHighscorePlayer(sampleDF).count()     should be(1L)
    analytics.getPlayerStatistics(sampleDF).count()     should be > 0L
    analytics.analyzeGameResults(sampleDF).count()      should be > 0L
    analytics.analyzeTimeControls(sampleDF).count()     should be > 0L
  }
}
