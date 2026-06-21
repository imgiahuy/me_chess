package engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UciEngineSpec extends AnyFunSuite with Matchers {

  test("EngineManager can be initialized") {
    val manager = EngineManager()
    manager should not be null
  }

  test("EngineManager can register engine configuration") {
    val manager = EngineManager()
    val config = UciEngineConfig(
      enginePath = "C:\\stockfish-windows-x86-64-avx2\\stockfish\\stockfish-windows-x86-64-avx2.exe",
      options = Map("Skill Level" -> "10"),
      defaultDepth = 10,
      defaultTimeMs = 500
    )
    
    val result = manager.registerEngine("test-engine", config)
    result.isSuccess shouldBe true
  }

  test("EngineManager can list engine configurations") {
    val manager = EngineManager()
    EngineManager.initializeDefault()
    manager.listEngineConfigs should not be empty
  }

  test("EngineManager can get engine configuration by name") {
    val manager = EngineManager()
    EngineManager.initializeDefault()
    manager.getEngineConfig("stockfish") should not be empty
  }

  test("EngineManager can check if engine is running") {
    val manager = EngineManager()
    manager.isEngineRunning("stockfish") shouldBe false
  }

  test("UciEngineConfig can be created") {
    val config = UciEngineConfig(
      enginePath = "test.exe",
      options = Map("Threads" -> "4"),
      defaultDepth = 15,
      defaultTimeMs = 1000
    )
    config.enginePath shouldEqual "test.exe"
    config.options shouldEqual Map("Threads" -> "4")
    config.defaultDepth shouldEqual 15
    config.defaultTimeMs shouldEqual 1000
  }
}
