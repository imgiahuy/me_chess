package engine

import model._
import scala.collection.mutable
import scala.util.{Try, Success, Failure}

/** Manager for UCI engine instances and configurations */
class EngineManager {
  
  private val engines: mutable.Map[String, UciEngine] = mutable.Map.empty
  private val configs: mutable.Map[String, UciEngineConfig] = mutable.Map.empty
  
  /** Register an engine configuration */
  def registerEngine(name: String, config: UciEngineConfig): Try[Unit] = Try {
    configs(name) = config
  }
  
  /** Get engine configuration by name */
  def getEngineConfig(name: String): Option[UciEngineConfig] = configs.get(name)
  
  /** List all registered engine configurations */
  def listEngineConfigs: List[(String, UciEngineConfig)] = configs.toList
  
  /** Initialize and start an engine instance */
  def startEngine(name: String): Try[UciEngine] = {
    configs.get(name) match {
      case Some(config) =>
        Try {
          val engine = UciEngine.create(config)
          engine.initialize()
          engines(name) = engine
          engine
        }
      case None =>
        Failure(new Exception(s"Engine configuration not found: $name"))
    }
  }
  
  /** Get running engine instance */
  def getEngine(name: String): Option[UciEngine] = engines.get(name)
  
  /** Stop and remove engine instance */
  def stopEngine(name: String): Try[Unit] = Try {
    engines.get(name).foreach { engine =>
      engine.shutdown()
      engines.remove(name)
    }
  }
  
  /** Stop all running engines */
  def stopAllEngines(): Unit = {
    engines.values.foreach(_.shutdown())
    engines.clear()
  }
  
  /** Check if engine is running */
  def isEngineRunning(name: String): Boolean = engines.contains(name)
  
  /** Get engine info without starting it */
  def getEngineInfo(name: String): Try[Map[String, String]] = {
    configs.get(name) match {
      case Some(config) =>
        Try {
          val engine = UciEngine.create(config)
          engine.initialize()
          val info = engine.getEngineInfo
          engine.shutdown()
          info
        }
      case None =>
        Failure(new Exception(s"Engine configuration not found: $name"))
    }
  }
}

/** Singleton engine manager instance */
object EngineManager {
  private val instance = new EngineManager()
  
  def apply(): EngineManager = instance
  
  /** Initialize with default Stockfish configuration */
  def initializeDefault(): Unit = {
    val stockfishPath = sys.env.getOrElse("STOCKFISH_PATH", "C:\\stockfish-windows-x86-64-avx2\\stockfish\\stockfish-windows-x86-64-avx2.exe")
    
    instance.registerEngine("stockfish", UciEngineConfig(
      enginePath = stockfishPath,
      options = Map(
        "Skill Level" -> "20",
        "Threads" -> "4",
        "Hash" -> "256"
      ),
      defaultDepth = 15,
      defaultTimeMs = 1000
    ))
    
    instance.registerEngine("stockfish-easy", UciEngineConfig(
      enginePath = stockfishPath,
      options = Map(
        "Skill Level" -> "5",
        "Threads" -> "2",
        "Hash" -> "128"
      ),
      defaultDepth = 10,
      defaultTimeMs = 500
    ))
    
    instance.registerEngine("stockfish-medium", UciEngineConfig(
      enginePath = stockfishPath,
      options = Map(
        "Skill Level" -> "10",
        "Threads" -> "2",
        "Hash" -> "128"
      ),
      defaultDepth = 12,
      defaultTimeMs = 750
    ))
  }
}
