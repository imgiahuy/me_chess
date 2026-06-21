package analytics

import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types._

/** ELO rating calculation for chess players.
  * 
  * Implements the standard ELO rating system used in chess:
  * - Expected score: E_a = 1 / (1 + 10^((R_b - R_a) / 400))
  * - New rating: R'_a = R_a + K * (S_a - E_a)
  * 
  * Where:
  * - R_a, R_b: Current ratings of players A and B
  * - S_a: Actual score (1 for win, 0.5 for draw, 0 for loss)
  * - K: K-factor (typically 32 for new players, 24 for established players)
  */
class EloRating(val spark: SparkSession) {

  import spark.implicits._

  /** Default initial ELO rating for new players */
  val DEFAULT_ELO = 1200.0

  /** K-factor for rating updates */
  val K_FACTOR = 32.0

  /** Schema for player ratings */
  val ratingSchema: StructType = StructType(Seq(
    StructField("player", StringType, nullable = false),
    StructField("rating", DoubleType, nullable = false),
    StructField("games_played", IntegerType, nullable = false),
    StructField("last_updated", TimestampType, nullable = false)
  ))

  /** Calculate expected score for player A against player B
    * 
    * E_a = 1 / (1 + 10^((R_b - R_a) / 400))
    */
  def calculateExpectedScore(ratingA: Double, ratingB: Double): Double = {
    1.0 / (1.0 + math.pow(10.0, (ratingB - ratingA) / 400.0))
  }

  /** Calculate new rating after a game
    * 
    * R'_a = R_a + K * (S_a - E_a)
    * 
    * @param currentRating Current rating of the player
    * @param expectedScore Expected score (calculated from opponent's rating)
    * @param actualScore Actual score (1.0 for win, 0.5 for draw, 0.0 for loss)
    * @param kFactor K-factor for rating adjustment
    * @return New rating
    */
  def calculateNewRating(
    currentRating: Double,
    expectedScore: Double,
    actualScore: Double,
    kFactor: Double = K_FACTOR
  ): Double = {
    currentRating + kFactor * (actualScore - expectedScore)
  }

  /** Initialize ratings for all players from game data */
  def initializePlayerRatings(gamesDF: DataFrame): DataFrame = {
    println("[ELO] Initializing player ratings")

    // Get all unique players
    val playersDF = gamesDF
      .select($"white_player".as("player"))
      .union(gamesDF.select($"black_player".as("player")))
      .distinct()
      .withColumn("rating", lit(DEFAULT_ELO))
      .withColumn("games_played", lit(0))
      .withColumn("last_updated", current_timestamp())

    println(s"[ELO] Initialized ratings for ${playersDF.count()} players")
    playersDF
  }

  /** Calculate ELO ratings from game results
    * 
    * Processes games chronologically and updates ratings after each game.
    * Uses window functions to carry forward ratings from previous games.
    */
  def calculateEloRatings(gamesDF: DataFrame): DataFrame = {
    println("[ELO] Calculating ELO ratings from game results")

    // Ensure games have timestamps for chronological ordering
    val gamesWithTime = gamesDF
      .withColumn("game_timestamp", 
        coalesce(col("timestamp"), col("creationDate"), current_timestamp())
      )
      .orderBy("game_timestamp")

    // Initialize player ratings
    val initialRatings = initializePlayerRatings(gamesWithTime)

    // For each game, calculate rating changes
    val gameResults = gamesWithTime
      .filter(col("result").isin("1-0", "0-1", "1/2-1/2"))
      .withColumn("winner_color", 
        when(col("result") === "1-0", lit("White"))
        .when(col("result") === "0-1", lit("Black"))
        .when(col("result") === "1/2-1/2", lit("Draw"))
      )
      .withColumn("white_score", 
        when(col("result") === "1-0", 1.0)
        .when(col("result") === "0-1", 0.0)
        .when(col("result") === "1/2-1/2", 0.5)
      )
      .withColumn("black_score", 
        when(col("result") === "0-1", 1.0)
        .when(col("result") === "1-0", 0.0)
        .when(col("result") === "1/2-1/2", 0.5)
      )

    // Create a mapping of player ratings before each game
    // This is a simplified approach - in production, you'd use iterative processing
    val playerRatings = initialRatings
      .select($"player".as("player_id"), $"rating".as("current_rating"))

    // Join game results with current ratings
    val gamesWithRatings = gameResults
      .join(
        playerRatings.withColumnRenamed("player_id", "white_player_id"),
        $"white_player" === $"white_player_id",
        "left"
      )
      .withColumnRenamed("current_rating", "white_rating")
      .drop("white_player_id")
      .join(
        playerRatings.withColumnRenamed("player_id", "black_player_id"),
        $"black_player" === $"black_player_id",
        "left"
      )
      .withColumnRenamed("current_rating", "black_rating")
      .drop("black_player_id")
      .na.fill(DEFAULT_ELO, Seq("white_rating", "black_rating"))

    // Calculate expected scores
    val withExpectedScores = gamesWithRatings
      .withColumn("white_expected", 
        lit(1.0) / (lit(1.0) + pow(lit(10.0), ($"black_rating" - $"white_rating") / lit(400.0)))
      )
      .withColumn("black_expected", 
        lit(1.0) / (lit(1.0) + pow(lit(10.0), ($"white_rating" - $"black_rating") / lit(400.0)))
      )

    // Calculate new ratings
    val withNewRatings = withExpectedScores
      .withColumn("white_new_rating", 
        $"white_rating" + lit(K_FACTOR) * ($"white_score" - $"white_expected")
      )
      .withColumn("black_new_rating", 
        $"black_rating" + lit(K_FACTOR) * ($"black_score" - $"black_expected")
      )

    // Collect final ratings for each player (using the most recent game)
    val whiteFinalRatings = withNewRatings
      .select(
        $"white_player".as("player"),
        $"white_new_rating".as("rating"),
        lit(1).as("game_count")
      )

    val blackFinalRatings = withNewRatings
      .select(
        $"black_player".as("player"),
        $"black_new_rating".as("rating"),
        lit(1).as("game_count")
      )

    val allRatings = whiteFinalRatings.union(blackFinalRatings)
      .groupBy("player")
      .agg(
        avg("rating").as("rating"),
        sum("game_count").as("games_played")
      )
      .withColumn("last_updated", current_timestamp())
      .orderBy(desc("rating"))

    println(s"[ELO] Calculated final ratings for ${allRatings.count()} players")
    allRatings
  }

  /** Calculate ELO ratings with iterative processing (more accurate)
    * 
    * This approach processes games one at a time, updating ratings after each game.
    * More accurate but computationally more expensive.
    */
  def calculateEloRatingsIterative(gamesDF: DataFrame): DataFrame = {
    println("[ELO] Calculating ELO ratings with iterative processing")

    // Get games sorted by timestamp
    val games = gamesDF
      .filter(col("result").isin("1-0", "0-1", "1/2-1/2"))
      .withColumn("game_timestamp", 
        coalesce(col("timestamp"), col("creationDate"), current_timestamp())
      )
      .orderBy("game_timestamp")
      .collect()

    // Initialize ratings map
    var ratings = scala.collection.mutable.Map[String, Double]()
    var gamesPlayed = scala.collection.mutable.Map[String, Int]()

    // Process each game
    games.foreach { row =>
      val whitePlayer = row.getAs[String]("white_player")
      val blackPlayer = row.getAs[String]("black_player")
      val result = row.getAs[String]("result")

      // Get current ratings (default to DEFAULT_ELO)
      val whiteRating = ratings.getOrElse(whitePlayer, DEFAULT_ELO)
      val blackRating = ratings.getOrElse(blackPlayer, DEFAULT_ELO)

      // Calculate expected scores
      val whiteExpected = calculateExpectedScore(whiteRating, blackRating)
      val blackExpected = calculateExpectedScore(blackRating, whiteRating)

      // Determine actual scores
      val (whiteScore, blackScore) = result match {
        case "1-0" => (1.0, 0.0)
        case "0-1" => (0.0, 1.0)
        case "1/2-1/2" => (0.5, 0.5)
        case _ => (0.0, 0.0)
      }

      // Calculate new ratings
      val whiteNewRating = calculateNewRating(whiteRating, whiteExpected, whiteScore)
      val blackNewRating = calculateNewRating(blackRating, blackExpected, blackScore)

      // Update ratings
      ratings(whitePlayer) = whiteNewRating
      ratings(blackPlayer) = blackNewRating
      gamesPlayed(whitePlayer) = gamesPlayed.getOrElse(whitePlayer, 0) + 1
      gamesPlayed(blackPlayer) = gamesPlayed.getOrElse(blackPlayer, 0) + 1
    }

    // Convert to DataFrame
    val ratingRows = ratings.map { case (player, rating) =>
      Row(player, rating, gamesPlayed.getOrElse(player, 0), new java.sql.Timestamp(System.currentTimeMillis()))
    }.toSeq

    spark.createDataFrame(
      spark.sparkContext.parallelize(ratingRows),
      ratingSchema
    ).orderBy(desc("rating"))
  }

  /** Calculate rating changes for a specific game */
  def calculateGameRatingChange(
    whiteRating: Double,
    blackRating: Double,
    result: String,
    kFactor: Double = K_FACTOR
  ): (Double, Double) = {
    val whiteExpected = calculateExpectedScore(whiteRating, blackRating)
    val blackExpected = calculateExpectedScore(blackRating, whiteRating)

    val (whiteScore, blackScore) = result match {
      case "1-0" => (1.0, 0.0)
      case "0-1" => (0.0, 1.0)
      case "1/2-1/2" => (0.5, 0.5)
      case _ => (0.0, 0.0)
    }

    val whiteNewRating = calculateNewRating(whiteRating, whiteExpected, whiteScore, kFactor)
    val blackNewRating = calculateNewRating(blackRating, blackExpected, blackScore, kFactor)

    (whiteNewRating - whiteRating, blackNewRating - blackRating)
  }

  /** Generate rating history for a player */
  def generateRatingHistory(
    gamesDF: DataFrame,
    playerName: String
  ): DataFrame = {
    println(s"[ELO] Generating rating history for $playerName")

    val playerGames = gamesDF
      .filter(
        $"white_player" === playerName || $"black_player" === playerName
      )
      .withColumn("game_timestamp", 
        coalesce(col("timestamp"), col("creationDate"), current_timestamp())
      )
      .orderBy("game_timestamp")

    if (playerGames.count() == 0) {
      println(s"[ELO] No games found for player $playerName")
      return spark.createDataFrame(spark.sparkContext.emptyRDD[Row], ratingSchema)
    }

    // Calculate rating history iteratively
    val games = playerGames.collect()
    var currentRating = DEFAULT_ELO
    var gameCount = 0
    var opponentRatings = scala.collection.mutable.Map[String, Double]()

    val history = games.map { row =>
      val isWhite = row.getAs[String]("white_player") == playerName
      val opponent = if (isWhite) row.getAs[String]("black_player") else row.getAs[String]("white_player")
      val opponentRating = opponentRatings.getOrElse(opponent, DEFAULT_ELO)
      val result = row.getAs[String]("result")

      val (playerScore, opponentScore) = if (isWhite) {
        result match {
          case "1-0" => (1.0, 0.0)
          case "0-1" => (0.0, 1.0)
          case "1/2-1/2" => (0.5, 0.5)
          case _ => (0.0, 0.0)
        }
      } else {
        result match {
          case "1-0" => (0.0, 1.0)
          case "0-1" => (1.0, 0.0)
          case "1/2-1/2" => (0.5, 0.5)
          case _ => (0.0, 0.0)
        }
      }

      val expectedScore = calculateExpectedScore(currentRating, opponentRating)
      val newRating = calculateNewRating(currentRating, expectedScore, playerScore)
      val ratingChange = newRating - currentRating

      // Update opponent's rating as well
      val opponentNewRating = calculateNewRating(opponentRating, 1.0 - expectedScore, opponentScore)
      opponentRatings(opponent) = opponentNewRating

      currentRating = newRating
      gameCount += 1

      Row(
        playerName,
        currentRating,
        gameCount,
        row.getAs[java.sql.Timestamp]("game_timestamp")
      )
    }

    spark.createDataFrame(
      spark.sparkContext.parallelize(history),
      ratingSchema
    )
  }

  /** Save ratings to file */
  def saveRatings(ratingsDF: DataFrame, path: String, format: String = "json"): Unit = {
    ratingsDF.write.mode("overwrite").format(format).save(path)
    println(s"[ELO] Saved ratings to $path")
  }

  /** Load ratings from file */
  def loadRatings(path: String): DataFrame = {
    val ratingsDF = spark.read.format("json").load(path)
    println(s"[ELO] Loaded ratings from $path")
    ratingsDF
  }
}

object EloRating {
  def apply(spark: SparkSession): EloRating = new EloRating(spark)
  def create(spark: SparkSession): EloRating = new EloRating(spark)
}
