package api

import engine.{EngineManager, UciEngineConfig}
import scala.util.{Try, Success, Failure}
import JsonCodecs._

/** Controller for managing UCI chess engines (Stockfish) */
class EngineController {
  
  private val engineManager = EngineManager()
  
  // Initialize default engine configurations
  EngineManager.initializeDefault()
  
  /** List all registered engine configurations */
  def listEngines(): List[JsonCodecs.EngineInfo] = {
    engineManager.listEngineConfigs.map { case (name, config) =>
      val isRunning = engineManager.isEngineRunning(name)
      JsonCodecs.EngineInfo(
        name = name,
        enginePath = config.enginePath,
        options = config.options,
        defaultDepth = config.defaultDepth,
        defaultTimeMs = config.defaultTimeMs,
        isRunning = isRunning
      )
    }
  }
  
  /** Get engine configuration by name */
  def getEngineConfig(name: String): Option[EngineConfigResponse] = {
    engineManager.getEngineConfig(name).map { config =>
      EngineConfigResponse(
        name = name,
        enginePath = config.enginePath,
        options = config.options,
        defaultDepth = config.defaultDepth,
        defaultTimeMs = config.defaultTimeMs
      )
    }
  }
  
  /** Start an engine instance */
  def startEngine(name: String): Either[String, EngineStatusResponse] = {
    engineManager.startEngine(name) match {
      case Success(engine) =>
        val info = engine.getEngineInfo
        engine.shutdown()
        Right(EngineStatusResponse(
          name = name,
          isRunning = true,
          info = Some(JsonCodecs.EngineInfoDetails(
            name = info.getOrElse("name", "Unknown"),
            author = info.getOrElse("author", "Unknown")
          ))
        ))
      case Failure(e) =>
        Left(s"Failed to start engine: ${e.getMessage}")
    }
  }
  
  /** Stop an engine instance */
  def stopEngine(name: String): Either[String, EngineStatusResponse] = {
    Try {
      engineManager.stopEngine(name)
      Right(EngineStatusResponse(
        name = name,
        isRunning = false,
        info = None
      ))
    }.getOrElse(Left(s"Failed to stop engine: $name"))
  }
  
  /** Get engine status */
  def getEngineStatus(name: String): Either[String, EngineStatusResponse] = {
    if (!engineManager.getEngineConfig(name).isDefined) {
      Left(s"Engine configuration not found: $name")
    } else {
      val isRunning = engineManager.isEngineRunning(name)
      val info = if (isRunning) {
        engineManager.getEngineInfo(name).toOption.map { infoMap =>
          EngineInfoDetails(
            name = infoMap.getOrElse("name", "Unknown"),
            author = infoMap.getOrElse("author", "Unknown")
          )
        }
      } else None
      
      Right(EngineStatusResponse(
        name = name,
        isRunning = isRunning,
        info = info
      ))
    }
  }
  
  /** Request position evaluation from engine */
  def evaluatePosition(name: String, fen: String, depth: Int = 15): Either[String, EvaluationResponse] = {
    engineManager.getEngine(name) match {
      case Some(engine) =>
        try {
          engine.setPosition(fen)
          val moveResult = engine.go(depth = depth, timeMs = 1000)
          import scala.concurrent.Await
          import scala.concurrent.duration._
          val result = Await.result(moveResult, 5.seconds)
          
          // Parse the UCI response to extract evaluation
          val score = parseEvaluation(result)
          Right(EvaluationResponse(
            engineName = name,
            fen = fen,
            depth = depth,
            score = score,
            bestMove = extractBestMove(result)
          ))
        } catch {
          case e: Exception => Left(s"Evaluation failed: ${e.getMessage}")
        }
      case None =>
        Left(s"Engine not running: $name")
    }
  }
  
  /** Request best move from engine */
  def getBestMove(name: String, fen: String, depth: Int = 15): Either[String, BestMoveResponse] = {
    engineManager.getEngine(name) match {
      case Some(engine) =>
        try {
          engine.setPosition(fen)
          val moveResult = engine.go(depth = depth, timeMs = 1000)
          import scala.concurrent.Await
          import scala.concurrent.duration._
          val result = Await.result(moveResult, 5.seconds)
          
          val bestMove = extractBestMove(result)
          Right(BestMoveResponse(
            engineName = name,
            fen = fen,
            depth = depth,
            bestMove = bestMove
          ))
        } catch {
          case e: Exception => Left(s"Best move calculation failed: ${e.getMessage}")
        }
      case None =>
        Left(s"Engine not running: $name")
    }
  }
  
  /** Register a custom engine configuration */
  def registerEngine(name: String, config: EngineConfigRequest): Either[String, EngineConfigResponse] = {
    val uciConfig = UciEngineConfig(
      enginePath = config.enginePath,
      options = config.options,
      defaultDepth = config.defaultDepth,
      defaultTimeMs = config.defaultTimeMs
    )
    
    Try {
      engineManager.registerEngine(name, uciConfig)
      EngineConfigResponse(
        name = name,
        enginePath = config.enginePath,
        options = config.options,
        defaultDepth = config.defaultDepth,
        defaultTimeMs = config.defaultTimeMs
      )
    }.toEither match {
      case Right(response) => Right(response)
      case Left(ex) => Left(ex.getMessage)
    }
  }
  
  /** Stop all running engines */
  def stopAllEngines(): Unit = {
    engineManager.stopAllEngines()
  }

  /** Get move suggestion for a position */
  def getMoveSuggestion(fen: String, engineName: String, depth: Int): Either[String, MoveSuggestionResponse] = {
    engineManager.getEngine(engineName) match {
      case Some(engine) =>
        try {
          engine.setPosition(fen)
          val moveResult = engine.go(depth = depth, timeMs = 1000)
          import scala.concurrent.Await
          import scala.concurrent.duration._
          val result = Await.result(moveResult, 5.seconds)
          
          val bestMove = extractBestMove(result)
          val score = extractScore(result)
          
          Right(MoveSuggestionResponse(
            bestMove = bestMove,
            score = score,
            depth = depth,
            engineName = engineName
          ))
        } catch {
          case e: Exception => Left(s"Move suggestion failed: ${e.getMessage}")
        }
      case None =>
        // Try to start the engine
        engineManager.startEngine(engineName) match {
          case scala.util.Success(engine) =>
            try {
              engine.setPosition(fen)
              val moveResult = engine.go(depth = depth, timeMs = 1000)
              import scala.concurrent.Await
              import scala.concurrent.duration._
              val result = Await.result(moveResult, 5.seconds)
              
              val bestMove = extractBestMove(result)
              val score = extractScore(result)
              
              Right(MoveSuggestionResponse(
                bestMove = bestMove,
                score = score,
                depth = depth,
                engineName = engineName
              ))
            } catch {
              case e: Exception => Left(s"Move suggestion failed: ${e.getMessage}")
            }
          case scala.util.Failure(_) =>
            Left(s"Engine not available: $engineName")
        }
    }
  }

  /** Extract best move from engine output */
  private def extractBestMove(output: String): String = {
    val lines = output.split("\n")
    lines.find(_.startsWith("bestmove")).map(_.split(" ")(1)).getOrElse("unknown")
  }

  /** Extract score from engine output */
  private def extractScore(output: String): String = {
    val lines = output.split("\n")
    lines.find(_.contains("score")).map { line =>
      if (line.contains("mate")) {
        val parts = line.split("mate")
        if (parts.length > 1) {
          val mateIn = parts(1).split(" ").find(_.nonEmpty).getOrElse("0")
          s"Mate in $mateIn"
        } else {
          "0.00"
        }
      } else if (line.contains("cp")) {
        val parts = line.split("cp")
        if (parts.length > 1) {
          val cpStr = parts(1).split(" ").find(_.nonEmpty).getOrElse("0")
          try {
            val score = cpStr.toInt / 100.0
            if (score >= 0) f"+$score%.2f" else f"$score%.2f"
          } catch {
            case _: NumberFormatException => "0.00"
          }
        } else {
          "0.00"
        }
      } else {
        "0.00"
      }
    }.getOrElse("0.00")
  }
  
  // Helper methods
  
  private def parseEvaluation(uciResponse: String): String = {
    // UCI response format: "bestmove e2e4 ponder ..." or includes "score cp 123"
    // For simplicity, return the raw response or extract score if present
    if (uciResponse.contains("score")) {
      val scoreMatch = uciResponse.split(" ").find(_.startsWith("score"))
      scoreMatch.map(_.replace("score", "").trim).getOrElse("0")
    } else {
      "0"
    }
  }
}
