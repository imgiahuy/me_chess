package analytics

import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/** Case class for game data representation in Spark.
 *  Defined at top level so it can be imported directly from tests.
 *  winner stores the winning color: "White", "Black", or None for draws.
 */
case class GameData(
  gameId: String,
  whitePlayer: String,
  blackPlayer: String,
  result: String,
  winner: Option[String],
  moveCount: Int,
  creationDate: java.sql.Date,
  timeControl: Option[String],
  whiteTimeMs: Option[Long],
  blackTimeMs: Option[Long]
)

/** Main Spark analytics engine for chess game data */
class ChessAnalytics(val spark: SparkSession) {

  import spark.implicits._

  /** Schema for game data */
  val gameSchema: StructType = StructType(Seq(
    StructField("gameId", StringType, nullable = false),
    StructField("whitePlayer", StringType, nullable = false),
    StructField("blackPlayer", StringType, nullable = false),
    StructField("result", StringType, nullable = false),
    StructField("winner", StringType, nullable = true),
    StructField("moveCount", IntegerType, nullable = false),
    StructField("creationDate", DateType, nullable = false),
    StructField("timeControl", StringType, nullable = true),
    StructField("whiteTimeMs", LongType, nullable = true),
    StructField("blackTimeMs", LongType, nullable = true)
  ))

  /** Read chess games from a PGN file.
   *  Handles multi-game PGN files by reading the full content and splitting
   *  on blank lines that precede a new tag section.
   */
  def readPgnFile(filePath: String): DataFrame = {
    // wholeTextFiles reads entire file content per partition
    val fileContent = spark.sparkContext
      .wholeTextFiles(filePath)
      .values
      .collect()
      .mkString("\n")

    // Split into individual game blocks: each game starts with "["
    // Games are separated by a blank line before the next tag block
    val gameTexts = fileContent
      .split("(?=\\n\\[)")
      .map(_.trim)
      .filter(_.startsWith("["))

    val rows = gameTexts.flatMap(parsePgnToGameData).map { g =>
      Row(
        g.gameId,
        g.whitePlayer,
        g.blackPlayer,
        g.result,
        g.winner.orNull,
        g.moveCount,
        g.creationDate,
        g.timeControl.orNull,
        g.whiteTimeMs.map(java.lang.Long.valueOf).orNull,
        g.blackTimeMs.map(java.lang.Long.valueOf).orNull
      )
    }

    spark.createDataFrame(
      spark.sparkContext.parallelize(rows.toSeq),
      gameSchema
    )
  }

  /** Read chess games from JSON file */
  def readJsonFile(filePath: String): DataFrame = {
    spark.read.schema(gameSchema).json(filePath)
  }

  /** Count total victories by player name.
   *  Resolves the winning color ("White"/"Black") to the actual player name.
   */
  def countVictories(gamesDF: DataFrame): DataFrame = {
    gamesDF
      .filter($"winner".isNotNull)
      .withColumn(
        "player",
        when($"winner" === "White", $"whitePlayer")
          .when($"winner" === "Black", $"blackPlayer")
      )
      .groupBy($"player")
      .agg(
        count("*").as("victories"),
        avg("moveCount").as("avgMovesPerVictory")
      )
      .orderBy(desc("victories"))
  }

  /** Find highscore (most wins) player */
  def findHighscorePlayer(gamesDF: DataFrame): DataFrame = {
    val victoriesDF = countVictories(gamesDF)
    
    victoriesDF
      .limit(1)
      .withColumn("achievement", lit("Most Victories"))
  }

  /** Get comprehensive player statistics */
  def getPlayerStatistics(gamesDF: DataFrame): DataFrame = {
    // Get games where player is white
    val whiteGames = gamesDF.select(
      $"whitePlayer".as("player"),
      $"gameId",
      $"moveCount",
      $"whiteTimeMs".as("playerTimeMs"),
      $"result",
      when($"winner" === "White", "victory")
        .when($"winner" === "Black", "defeat")
        .when($"winner".isNull, "draw")
        .as("gameOutcome")
    )

    // Get games where player is black
    val blackGames = gamesDF.select(
      $"blackPlayer".as("player"),
      $"gameId",
      $"moveCount",
      $"blackTimeMs".as("playerTimeMs"),
      $"result",
      when($"winner" === "Black", "victory")
        .when($"winner" === "White", "defeat")
        .when($"winner".isNull, "draw")
        .as("gameOutcome")
    )

    // Union all games
    val allGames = whiteGames.unionByName(blackGames)

    // Calculate statistics
    allGames.groupBy($"player")
      .agg(
        count("*").as("totalGames"),
        sum(when($"gameOutcome" === "victory", 1).otherwise(0)).as("victories"),
        sum(when($"gameOutcome" === "defeat", 1).otherwise(0)).as("defeats"),
        sum(when($"gameOutcome" === "draw", 1).otherwise(0)).as("draws"),
        avg("moveCount").as("avgMovesPerGame"),
        sum(coalesce($"playerTimeMs", lit(0L))).as("totalTimeMs")
      )
      .withColumn("winRate", $"victories" / $"totalGames")
      .orderBy(desc("winRate"))
  }

  /** Find best player by custom metric */
  def findBestPlayerByMetric(gamesDF: DataFrame, metric: String = "winRate"): DataFrame = {
    val statsDF = getPlayerStatistics(gamesDF)
    
    metric.toLowerCase match {
      case "winrate" | "win_rate" =>
        statsDF.orderBy(desc("winRate")).limit(1)
      case "victories" =>
        statsDF.orderBy(desc("victories")).limit(1)
      case "totalgames" | "total_games" =>
        statsDF.orderBy(desc("totalGames")).limit(1)
      case "avgmoves" | "avg_moves" =>
        statsDF.orderBy(desc("avgMovesPerGame")).limit(1)
      case _ =>
        statsDF.orderBy(desc("winRate")).limit(1)
    }
  }

  /** Analyze game results distribution with percentage share */
  def analyzeGameResults(gamesDF: DataFrame): DataFrame = {
    // Window functions cannot be used inside .agg(), so compute counts first,
    // then apply a window over the count column.
    val windowAll = Window.orderBy(lit(1))
      .rowsBetween(Window.unboundedPreceding, Window.unboundedFollowing)

    gamesDF
      .groupBy("result")
      .agg(count("*").as("count"))
      .withColumn(
        "percentage",
        round($"count" / sum("count").over(windowAll) * 100, 2)
      )
      .orderBy(desc("count"))
  }

  /** Time control analysis */
  def analyzeTimeControls(gamesDF: DataFrame): DataFrame = {
    gamesDF
      .filter($"timeControl".isNotNull)
      .groupBy("timeControl")
      .agg(
        count("*").as("gamesPlayed"),
        avg("moveCount").as("avgMoves"),
        avg("whiteTimeMs").as("avgWhiteTimeMs"),
        avg("blackTimeMs").as("avgBlackTimeMs")
      )
      .orderBy(desc("gamesPlayed"))
  }

  /** Private helper: parse one PGN game block into a GameData row */
  private def parsePgnToGameData(pgnText: String): Option[GameData] = {
    try {
      val lines = pgnText.split("\n")
      val tagPattern = """\[(\w+)\s+"([^"]*)"\]""".r
      
      var tags = Map[String, String]()
      var moveLines = List[String]()
      var inTags = true
      
      lines.foreach { line =>
        val trimmed = line.trim
        if (trimmed.startsWith("[")) {
          tagPattern.findFirstMatchIn(trimmed).foreach { m =>
            tags += (m.group(1) -> m.group(2))
          }
        } else if (trimmed.nonEmpty && !trimmed.startsWith("%")) {
          inTags = false
          moveLines :+= trimmed
        }
      }
      
      val movesText = moveLines.mkString(" ")
      val moveCount = movesText.split("\\s+").count(_.matches("[1-9]\\d*\\.*"))
      
      val result = tags.getOrElse("Result", "*")
      val winner = result match {
        case "1-0" => Some("White")
        case "0-1" => Some("Black")
        case "1/2-1/2" => None
        case "*" => None
        case _ => None
      }
      
      Some(GameData(
        gameId = java.util.UUID.randomUUID().toString,
        whitePlayer = tags.getOrElse("White", "Unknown"),
        blackPlayer = tags.getOrElse("Black", "Unknown"),
        result = result,
        winner = winner,
        moveCount = moveCount,
        creationDate = java.sql.Date.valueOf(tags.getOrElse("Date", "2024-01-01").replaceAll("\\?.?", "01")),
        timeControl = tags.get("TimeControl"),
        whiteTimeMs = None,
        blackTimeMs = None
      ))
    } catch {
      case _: Exception => None
    }
  }

  /** Save results to file */
  def saveResults(df: DataFrame, path: String, format: String = "json"): Unit = {
    df.write.mode("overwrite").format(format).save(path)
  }
}

object ChessAnalytics {
  def apply(spark: SparkSession): ChessAnalytics = new ChessAnalytics(spark)
  def create(spark: SparkSession): ChessAnalytics  = new ChessAnalytics(spark)
}
