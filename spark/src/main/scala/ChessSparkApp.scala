import analytics.ChessAnalytics
import streaming.ChessKafkaStreaming
import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.functions._
import com.mongodb.client.{MongoClients, MongoClient}
import com.mongodb.client.model.Filters
import org.bson.Document
import scala.jdk.CollectionConverters._

/** Main Spark application for chess analytics */
object ChessSparkApp {
  
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder
      .appName("ChessAnalytics")
      .master("local[*]")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .getOrCreate()

    try {
      val analytics = ChessAnalytics.create(spark)
      val streaming = ChessKafkaStreaming.create(spark)
      
      println("=== Chess Analytics Spark Application ===")
      
      // Parse command line arguments
      if (args.length < 1) {
        printUsage()
        return
      }
      
      val mode = args(0).toLowerCase
      
      mode match {
        case "file"  => processFileData(analytics, args.drop(1))
        case "kafka" => processKafkaData(streaming, args.drop(1))
        case "batch" => runBatchAnalytics(analytics, args.drop(1))
        case "mongo" => runMongoAnalytics(analytics, args.drop(1))
        case _ =>
          println(s"Unknown mode: $mode")
          printUsage()
      }
      
    } catch {
      case e: Exception =>
        println(s"Error: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      spark.stop()
    }
  }
  
  /** Process data from files */
  def processFileData(analytics: ChessAnalytics, args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: file <input_path> <output_path> [format]")
      return
    }
    
    val inputPath = args(0)
    val outputPath = args(1)
    val format = if (args.length > 2) args(2) else "json"
    
    println(s"Processing file data from: $inputPath")
    
    // Read data (support both PGN and JSON)
    val gamesDF = if (inputPath.endsWith(".pgn")) {
      analytics.readPgnFile(inputPath)
    } else {
      analytics.readJsonFile(inputPath)
    }
    
    println(s"Loaded ${gamesDF.count()} games")
    
    // Run analytics
    runAllAnalytics(analytics, gamesDF, outputPath, format)
  }
  
  /** Process streaming data from Kafka */
  def processKafkaData(streaming: ChessKafkaStreaming, args: Array[String]): Unit = {
    if (args.length < 2) {
      println("Usage: kafka <bootstrap_servers> <output_path>")
      return
    }
    
    val bootstrapServers = args(0)
    val outputPath = args(1)
    
    println(s"Starting Kafka streaming from: $bootstrapServers")
    
    // Read from Kafka topics
    val topics = Seq("chess-events", "chess-game-created", "chess-move-made", "chess-game-ended")
    val streamDF = streaming.readFromKafka(bootstrapServers, topics, "earliest")
    
    // Calculate real-time statistics
    val realTimeStats = streaming.calculateRealTimeStats(streamDF)
    val playerPerformance = streaming.aggregatePlayerPerformance(streamDF, "5 minutes")
    val activeGames = streaming.monitorActiveGames(streamDF)
    
    // Start streaming queries
    streaming.startConsoleQuery(realTimeStats, "real-time-stats", "30 seconds")
    streaming.startFileSink(playerPerformance, s"$outputPath/player-performance", "json", "player-performance", "1 minute")
    streaming.startConsoleQuery(activeGames, "active-games", "30 seconds")
    
    // Block until any streaming query terminates or Ctrl+C
    println("Streaming started. Press Ctrl+C to stop.")
    streaming.spark.streams.awaitAnyTermination()
  }
  
  /** Run batch analytics on sample data */
  def runBatchAnalytics(analytics: ChessAnalytics, args: Array[String]): Unit = {
    val outputPath = if (args.length > 0) args(0) else "/tmp/chess-analytics"
    
    println("Running batch analytics on sample data...")
    
    // Create sample data
    val sampleGames = createSampleGames(analytics)
    
    runAllAnalytics(analytics, sampleGames, outputPath, "json")
  }
  
  /** Run all analytics functions */
  def runAllAnalytics(analytics: ChessAnalytics, gamesDF: DataFrame, outputPath: String, format: String): Unit = {
    println("Running comprehensive analytics...")
    
    // 1. Count victories
    val victories = analytics.countVictories(gamesDF)
    analytics.saveResults(victories, s"$outputPath/victories", format)
    println("✓ Victories analysis completed")
    
    // 2. Find highscore player
    val highscore = analytics.findHighscorePlayer(gamesDF)
    analytics.saveResults(highscore, s"$outputPath/highscore", format)
    println("✓ Highscore player analysis completed")
    
    // 3. Player statistics
    val playerStats = analytics.getPlayerStatistics(gamesDF)
    analytics.saveResults(playerStats, s"$outputPath/player-stats", format)
    println("✓ Player statistics analysis completed")
    
    // 4. Best player by different metrics
    val bestByWinRate = analytics.findBestPlayerByMetric(gamesDF, "winRate")
    analytics.saveResults(bestByWinRate, s"$outputPath/best-winrate", format)
    
    val bestByVictories = analytics.findBestPlayerByMetric(gamesDF, "victories")
    analytics.saveResults(bestByVictories, s"$outputPath/best-victories", format)
    
    val bestByGames = analytics.findBestPlayerByMetric(gamesDF, "totalGames")
    analytics.saveResults(bestByGames, s"$outputPath/best-games", format)
    println("✓ Best player analysis completed")
    
    // 5. Game results distribution
    val resultsDist = analytics.analyzeGameResults(gamesDF)
    analytics.saveResults(resultsDist, s"$outputPath/results-distribution", format)
    println("✓ Results distribution analysis completed")
    
    // 6. Time control analysis
    val timeControlAnalysis = analytics.analyzeTimeControls(gamesDF)
    analytics.saveResults(timeControlAnalysis, s"$outputPath/time-controls", format)
    println("✓ Time control analysis completed")
    
    // Display summary
    println(s"\n=== Analytics Summary ===")
    println(s"Total games processed: ${gamesDF.count()}")
    
    victories.show(5, truncate = false)
    highscore.show(5, truncate = false)
    playerStats.show(5, truncate = false)
    resultsDist.show(5, truncate = false)
    
    println(s"\nAll results saved to: $outputPath")
  }
  
  /** Read real games from MongoDB and run analytics */
  def runMongoAnalytics(analytics: ChessAnalytics, args: Array[String]): Unit = {
    val mongoUri    = if (args.length > 0) args(0) else sys.env.getOrElse("MONGODB_URI", "mongodb://localhost:27017")
    val dbName      = if (args.length > 1) args(1) else sys.env.getOrElse("MONGODB_DATABASE", "chess")
    val outputPath  = if (args.length > 2) args(2) else sys.env.getOrElse("SPARK_OUTPUT_PATH", "/tmp/chess-analytics")

    println(s"Reading real games from MongoDB: $mongoUri / $dbName")

    val client: MongoClient = MongoClients.create(mongoUri)
    try {
      val db          = client.getDatabase(dbName)
      val gamesColl   = db.getCollection("games")
      val playersColl = db.getCollection("players")
      val movesColl   = db.getCollection("moves")

      // Build player id -> name map
      val playerMap: Map[Int, String] = playersColl.find().asScala.flatMap { doc =>
        Option(doc.getInteger("id")).map(id => id.intValue -> doc.getString("name"))
      }.toMap

      val rows = gamesColl.find().asScala.flatMap { doc =>
        val gameId       = doc.getString("id")
        val whiteId      = Option(doc.getInteger("whitePlayerId")).map(_.intValue).getOrElse(-1)
        val blackId      = Option(doc.getInteger("blackPlayerId")).map(_.intValue).getOrElse(-1)
        val whiteName    = playerMap.getOrElse(whiteId, s"Player$whiteId")
        val blackName    = playerMap.getOrElse(blackId, s"Player$blackId")
        val resultStr    = Option(doc.getString("result")).getOrElse("")
        val moveCount    = movesColl.countDocuments(Filters.eq("gameId", gameId)).toInt
        val creationDate = Option(doc.getDate("creationDate"))
          .map(d => new java.sql.Date(d.getTime))
          .getOrElse(new java.sql.Date(System.currentTimeMillis()))
        val tcStr        = Option(doc.getString("timeControl"))
        val tcLabel      = tcStr.flatMap { tc =>
          val ms = tc.split(",").headOption.flatMap(_.toLongOption).getOrElse(0L)
          if (ms <= 0L)             None
          else if (ms <= 90000L)    Some("bullet")
          else if (ms <= 300000L)   Some("blitz")
          else if (ms <= 900000L)   Some("rapid")
          else                      Some("classical")
        }
        val (pgResult, winner) = resultStr match {
          case s if s.startsWith("checkmate:White")    => ("1-0",     Some("White"))
          case s if s.startsWith("checkmate:Black")    => ("0-1",     Some("Black"))
          case s if s.startsWith("resignation:White")  => ("1-0",     Some("White"))
          case s if s.startsWith("resignation:Black")  => ("0-1",     Some("Black"))
          case s if s.startsWith("timeout:White")      => ("1-0",     Some("White"))
          case s if s.startsWith("timeout:Black")      => ("0-1",     Some("Black"))
          case s if s.startsWith("draw:")              => ("1/2-1/2", None)
          case _                                        => return None  // skip ongoing
        }
        Some(Row(gameId, whiteName, blackName, pgResult, winner.orNull,
                 moveCount, creationDate, tcLabel.orNull,
                 null: java.lang.Long, null: java.lang.Long))
      }.toSeq

      if (rows.isEmpty) {
        println("No completed games found in MongoDB — falling back to sample data")
        runBatchAnalytics(analytics, Array(outputPath))
      } else {
        println(s"Found ${rows.size} completed games in MongoDB")
        val gamesDF = analytics.spark.createDataFrame(
          analytics.spark.sparkContext.parallelize(rows),
          analytics.gameSchema
        )
        runAllAnalytics(analytics, gamesDF, outputPath, "json")
      }
    } finally {
      client.close()
    }
  }

  /** Create sample games data for testing.
   *  Uses manual Row creation to avoid TypeTag/Encoder issues in Scala 3.
   */
  def createSampleGames(analytics: ChessAnalytics): DataFrame = {
    import org.apache.spark.sql.Row

    val rows = Seq(
      Row("game1",  "Alice",   "Bob",     "1-0",     "White", 45, java.sql.Date.valueOf("2024-01-15"), "blitz",     180000L,  120000L),
      Row("game2",  "Bob",     "Charlie", "0-1",     "Black", 67, java.sql.Date.valueOf("2024-01-16"), "rapid",     600000L,  540000L),
      Row("game3",  "Alice",   "Charlie", "1/2-1/2", null,    89, java.sql.Date.valueOf("2024-01-17"), "classical", 1800000L, 1740000L),
      Row("game4",  "David",   "Alice",   "1-0",     "White", 34, java.sql.Date.valueOf("2024-01-18"), "blitz",     240000L,  180000L),
      Row("game5",  "Bob",     "David",   "0-1",     "Black", 56, java.sql.Date.valueOf("2024-01-19"), "rapid",     480000L,  420000L),
      Row("game6",  "Charlie", "David",   "1-0",     "White", 78, java.sql.Date.valueOf("2024-01-20"), "classical", 2100000L, 1980000L),
      Row("game7",  "Alice",   "David",   "1/2-1/2", null,    92, java.sql.Date.valueOf("2024-01-21"), "classical", 2400000L, 2340000L),
      Row("game8",  "Bob",     "Alice",   "1-0",     "White", 41, java.sql.Date.valueOf("2024-01-22"), "blitz",     150000L,  90000L),
      Row("game9",  "Charlie", "Bob",     "0-1",     "Black", 63, java.sql.Date.valueOf("2024-01-23"), "rapid",     510000L,  450000L),
      Row("game10", "David",   "Charlie", "1-0",     "White", 55, java.sql.Date.valueOf("2024-01-24"), "rapid",     540000L,  480000L)
    )

    analytics.spark.createDataFrame(
      analytics.spark.sparkContext.parallelize(rows),
      analytics.gameSchema
    )
  }
  
  /** Print usage information */
  def printUsage(): Unit = {
    println("""
      |Chess Analytics Spark Application
      |
      |Usage:
      |  ChessSparkApp <mode> [options]
      |
      |Modes:
      |  file <input_path> <output_path> [format]  - Process data from files
      |  kafka <bootstrap_servers> <output_path>   - Process streaming data from Kafka
      |  batch [output_path]                       - Run batch analytics on sample data
      |
      |Examples:
      |  ChessSparkApp file data/games.pgn /tmp/results json
      |  ChessSparkApp file data/games.json /tmp/results
      |  ChessSparkApp kafka localhost:9092 /tmp/streaming-results
      |  ChessSparkApp batch /tmp/batch-results
      |
      |File formats supported:
      |  - PGN (.pgn) - Portable Game Notation
      |  - JSON (.json) - Structured game data
      |
      |Output formats:
      |  - json (default)
      |  - parquet
      |  - csv
    """.stripMargin)
  }
}
