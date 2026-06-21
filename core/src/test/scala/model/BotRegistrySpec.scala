package model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BotRegistrySpec extends AnyFunSuite with Matchers {

  test("BotRegistry lists default bots") {
    val registry = BotRegistry.create()
    val bots = registry.listBots()
    
    bots should not be empty
    bots.exists(_.botId == "random-bot") shouldBe true
    bots.exists(_.botId == "capture-bot") shouldBe true
  }

  test("BotRegistry can get a specific bot by ID") {
    val registry = BotRegistry.create()
    val bot = registry.getBot("random-bot")
    
    bot should not be empty
    bot.get.displayName shouldEqual "Random Bot"
    bot.get.family shouldEqual BotFamily.Heuristic
  }

  test("BotRegistry returns None for non-existent bot") {
    val registry = BotRegistry.create()
    val bot = registry.getBot("non-existent-bot")
    
    bot shouldBe empty
  }

  test("BotRegistry can register a new bot") {
    val registry = BotRegistry.create()
    val request = RegisterBotRequest(
      botId = "custom-bot",
      displayName = "Custom Bot",
      family = "heuristic",
      strategyType = "custom",
      engineType = "none",
      modelVersion = "1.0",
      author = Some("Test Author"),
      description = Some("A custom test bot")
    )
    
    val result = registry.registerBot(request)
    
    result should matchPattern { case Right(_) => }
    val bot = result.toOption.get
    bot.botId shouldEqual "custom-bot"
    bot.displayName shouldEqual "Custom Bot"
  }

  test("BotRegistry rejects duplicate bot ID") {
    val registry = BotRegistry.create()
    val request = RegisterBotRequest(
      botId = "random-bot",
      displayName = "Duplicate Bot",
      family = "heuristic",
      strategyType = "random",
      engineType = "none",
      modelVersion = "1.0"
    )
    
    val result = registry.registerBot(request)
    
    result should matchPattern { case Left(_) => }
  }

  test("BotRegistry rejects invalid bot family") {
    val registry = BotRegistry.create()
    val request = RegisterBotRequest(
      botId = "invalid-bot",
      displayName = "Invalid Bot",
      family = "invalid-family",
      strategyType = "random",
      engineType = "none",
      modelVersion = "1.0"
    )
    
    val result = registry.registerBot(request)
    
    result should matchPattern { case Left(_) => }
  }

  test("BotRegistry can unregister a bot") {
    val registry = BotRegistry.create()
    val request = RegisterBotRequest(
      botId = "temp-bot",
      displayName = "Temp Bot",
      family = "heuristic",
      strategyType = "random",
      engineType = "none",
      modelVersion = "1.0"
    )
    
    registry.registerBot(request)
    val result = registry.unregisterBot("temp-bot")
    
    result should matchPattern { case Right(_) => }
    registry.getBot("temp-bot") shouldBe empty
  }

  test("BotRegistry cannot unregister non-existent bot") {
    val registry = BotRegistry.create()
    val result = registry.unregisterBot("non-existent-bot")
    
    result should matchPattern { case Left(_) => }
  }

  test("BotRegistry can update bot availability") {
    val registry = BotRegistry.create()
    val result = registry.updateAvailability("random-bot", available = false, Some("Maintenance"))
    
    result should matchPattern { case Right(_) => }
    val bot = result.toOption.get
    bot.available shouldBe false
    bot.unavailableReason shouldBe Some("Maintenance")
  }

  test("BotRegistry cannot update non-existent bot availability") {
    val registry = BotRegistry.create()
    val result = registry.updateAvailability("non-existent-bot", available = false)
    
    result should matchPattern { case Left(_) => }
  }

  test("BotFamily can parse valid family strings") {
    BotFamily.fromString("heuristic") shouldBe Some(BotFamily.Heuristic)
    BotFamily.fromString("uci-engine") shouldBe Some(BotFamily.UciEngine)
    BotFamily.fromString("ai-service") shouldBe Some(BotFamily.AiService)
    BotFamily.fromString("neural-network") shouldBe Some(BotFamily.NeuralNetwork)
  }

  test("BotFamily returns None for invalid family strings") {
    BotFamily.fromString("invalid") shouldBe empty
  }
}
