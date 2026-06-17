package streaming

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.streaming.Trigger

/** Spark Streaming integration for reading chess events from Kafka */
class ChessKafkaStreaming(val spark: SparkSession) {

  import spark.implicits._

  /** Schema for chess game events from Kafka */
  val gameEventSchema: StructType = StructType(Seq(
    StructField("eventType", StringType, nullable = false),
    StructField("gameId", StringType, nullable = false),
    StructField("timestamp", TimestampType, nullable = false),
    StructField("whitePlayer", StringType, nullable = true),
    StructField("blackPlayer", StringType, nullable = true),
    StructField("move", StringType, nullable = true),
    StructField("moveNumber", IntegerType, nullable = true),
    StructField("gameResult", StringType, nullable = true),
    StructField("winner", StringType, nullable = true),
    StructField("timeControl", StringType, nullable = true),
    StructField("whiteTimeMs", LongType, nullable = true),
    StructField("blackTimeMs", LongType, nullable = true)
  ))

  /** Read chess events from Kafka stream */
  def readFromKafka(
    bootstrapServers: String,
    topics: Seq[String],
    startingOffset: String = "latest"
  ): DataFrame = {
    spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrapServers)
      .option("subscribe", topics.mkString(","))
      .option("startingOffsets", startingOffset)
      .option("failOnDataLoss", "false")
      .load()
      .selectExpr("CAST(value AS STRING) as json")
      .select(from_json($"json", gameEventSchema).as("data"))
      .select("data.*")
      .withWatermark("timestamp", "10 minutes")
  }

  /** Process game creation events */
  def processGameCreated(streamDF: DataFrame): DataFrame = {
    streamDF
      .filter($"eventType" === "game-created")
      .select(
        $"gameId",
        $"whitePlayer",
        $"blackPlayer",
        $"timestamp".as("creationTime"),
        $"timeControl"
      )
  }

  /** Process move events and calculate real-time statistics */
  def processMoveEvents(streamDF: DataFrame): DataFrame = {
    streamDF
      .filter($"eventType" === "move-made")
      .select(
        $"gameId",
        $"whitePlayer",
        $"blackPlayer",
        $"move",
        $"moveNumber",
        $"timestamp",
        $"whiteTimeMs",
        $"blackTimeMs"
      )
      .withWatermark("timestamp", "5 minutes")
  }

  /** Process game end events */
  def processGameEnded(streamDF: DataFrame): DataFrame = {
    streamDF
      .filter($"eventType" === "game-ended")
      .select(
        $"gameId",
        $"whitePlayer",
        $"blackPlayer",
        $"gameResult",
        $"winner",
        $"timestamp".as("endTime"),
        $"timeControl"
      )
  }

  /** Calculate real-time player statistics from streaming data */
  def calculateRealTimeStats(streamDF: DataFrame): DataFrame = {
    val gameEndEvents = processGameEnded(streamDF)
    
    gameEndEvents
      .groupBy(
        window($"endTime", "1 minute"),
        $"whitePlayer",
        $"blackPlayer",
        $"winner"
      )
      .agg(
        count("*").as("gamesInWindow"),
        sum(when($"winner" === "White", 1).otherwise(0)).as("whiteWins"),
        sum(when($"winner" === "Black", 1).otherwise(0)).as("blackWins"),
        sum(when($"winner".isNull, 1).otherwise(0)).as("draws")
      )
      .select(
        $"window.start".as("windowStart"),
        $"window.end".as("windowEnd"),
        $"whitePlayer",
        $"blackPlayer",
        $"gamesInWindow",
        $"whiteWins",
        $"blackWins",
        $"draws"
      )
  }

  /** Aggregate player performance over time windows */
  def aggregatePlayerPerformance(streamDF: DataFrame, windowDuration: String = "10 minutes"): DataFrame = {
    val gameEndEvents = processGameEnded(streamDF)
    
    // Get individual player results
    val whiteResults = gameEndEvents.select(
      $"whitePlayer".as("player"),
      $"endTime".as("timestamp"),
      when($"winner" === "White", "win")
        .when($"winner" === "Black", "loss")
        .otherwise("draw").as("result"),
      $"timeControl"
    )
    
    val blackResults = gameEndEvents.select(
      $"blackPlayer".as("player"),
      $"endTime".as("timestamp"),
      when($"winner" === "Black", "win")
        .when($"winner" === "White", "loss")
        .otherwise("draw").as("result"),
      $"timeControl"
    )
    
    val allResults = whiteResults.unionByName(blackResults)
    
    allResults
      .withWatermark("timestamp", "5 minutes")
      .groupBy(
        window($"timestamp", windowDuration),
        $"player",
        $"timeControl"
      )
      .agg(
        count("*").as("gamesPlayed"),
        sum(when($"result" === "win", 1).otherwise(0)).as("wins"),
        sum(when($"result" === "loss", 1).otherwise(0)).as("losses"),
        sum(when($"result" === "draw", 1).otherwise(0)).as("draws")
      )
      .withColumn("winRate", $"wins" / $"gamesPlayed")
      .select(
        $"window.start".as("windowStart"),
        $"window.end".as("windowEnd"),
        $"player",
        $"timeControl",
        $"gamesPlayed",
        $"wins",
        $"losses",
        $"draws",
        $"winRate"
      )
  }

  /** Start streaming query to write results to console */
  def startConsoleQuery(
    streamDF: DataFrame,
    queryName: String,
    triggerDuration: String = "1 minute"
  ): Unit = {
    val query = streamDF
      .writeStream
      .outputMode("update")
      .format("console")
      .option("truncate", "false")
      .option("numRows", "20")
      .trigger(Trigger.ProcessingTime(triggerDuration))
      .queryName(queryName)
      .start()
    
    println(s"Started streaming query: $queryName")
  }

  /** Start streaming query to write results to Kafka */
  def startKafkaSink(
    streamDF: DataFrame,
    topic: String,
    bootstrapServers: String,
    queryName: String,
    triggerDuration: String = "1 minute"
  ): Unit = {
    val query = streamDF
      .selectExpr("to_json(struct(*)) AS value")
      .writeStream
      .outputMode("update")
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrapServers)
      .option("topic", topic)
      .option("checkpointLocation", s"/tmp/checkpoint-$queryName")
      .trigger(Trigger.ProcessingTime(triggerDuration))
      .queryName(queryName)
      .start()
    
    println(s"Started Kafka sink query: $queryName -> $topic")
  }

  /** Start streaming query to write results to file system */
  def startFileSink(
    streamDF: DataFrame,
    outputPath: String,
    format: String = "json",
    queryName: String,
    triggerDuration: String = "1 minute"
  ): Unit = {
    val query = streamDF
      .writeStream
      .outputMode("append")
      .format(format)
      .option("path", outputPath)
      .option("checkpointLocation", s"/tmp/checkpoint-$queryName")
      .trigger(Trigger.ProcessingTime(triggerDuration))
      .queryName(queryName)
      .start()
    
    println(s"Started file sink query: $queryName -> $outputPath")
  }

  /** Monitor active games in real-time */
  def monitorActiveGames(streamDF: DataFrame): DataFrame = {
    val gameCreated = processGameCreated(streamDF)
    val gameEnded = processGameEnded(streamDF)
    
    // Join to find active games (created but not ended)
    // This is a simplified approach - in practice you'd need proper state management
    gameCreated
      .withWatermark("creationTime", "10 minutes")
      .select(
        $"gameId",
        $"whitePlayer",
        $"blackPlayer",
        $"creationTime",
        $"timeControl",
        lit("active").as("status")
      )
  }
}

object ChessKafkaStreaming {
  def create(spark: SparkSession): ChessKafkaStreaming = new ChessKafkaStreaming(spark)
}
