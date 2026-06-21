package model

import java.time.Instant

/** Bot family classification */
enum BotFamily:
  case Heuristic
  case UciEngine
  case AiService
  case NeuralNetwork

object BotFamily:
  def fromString(value: String): Option[BotFamily] = value.trim.toLowerCase match
    case "heuristic" => Some(Heuristic)
    case "uci-engine" | "uci" => Some(UciEngine)
    case "ai-service" => Some(AiService)
    case "neural-network" | "nn" => Some(NeuralNetwork)
    case _ => None

/** Rich bot descriptor with metadata for tournament registration */
case class BotDescriptor(
    botId: String,
    displayName: String,
    family: BotFamily,
    strategyType: String,
    engineType: String,
    modelVersion: String,
    available: Boolean = true,
    unavailableReason: Option[String] = None,
    author: Option[String] = None,
    description: Option[String] = None,
    createdAt: Instant = Instant.now()
)

/** Bot registration request */
case class RegisterBotRequest(
    botId: String,
    displayName: String,
    family: String,
    strategyType: String,
    engineType: String,
    modelVersion: String,
    author: Option[String] = None,
    description: Option[String] = None
)

/** Bot registry for managing available bots with rich metadata */
trait BotRegistry:
  def listBots(): List[BotDescriptor]
  def getBot(botId: String): Option[BotDescriptor]
  def registerBot(request: RegisterBotRequest): Either[String, BotDescriptor]
  def unregisterBot(botId: String): Either[String, Unit]
  def updateAvailability(botId: String, available: Boolean, reason: Option[String] = None): Either[String, BotDescriptor]

/** In-memory implementation of bot registry */
class InMemoryBotRegistry extends BotRegistry:
  private var bots: Map[String, BotDescriptor] = Map.empty

  // Initialize with default bots
  private def initializeDefaults(): Unit =
    val defaultBots = List(
      BotDescriptor(
        botId = "random-bot",
        displayName = "Random Bot",
        family = BotFamily.Heuristic,
        strategyType = "random",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Makes completely random moves. Perfect for beginners.")
      ),
      BotDescriptor(
        botId = "capture-bot",
        displayName = "Capture Bot",
        family = BotFamily.Heuristic,
        strategyType = "capture-first",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Prioritizes capturing pieces and center control.")
      ),
      BotDescriptor(
        botId = "greedy-bot",
        displayName = "Greedy Bot",
        family = BotFamily.Heuristic,
        strategyType = "material-greedy",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Always captures the most valuable piece available.")
      ),
      BotDescriptor(
        botId = "defensive-bot",
        displayName = "Defensive Bot",
        family = BotFamily.Heuristic,
        strategyType = "defensive",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Prioritizes king safety and solid positions.")
      ),
      BotDescriptor(
        botId = "aggressive-bot",
        displayName = "Aggressive Bot",
        family = BotFamily.Heuristic,
        strategyType = "aggressive",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Attacks relentlessly and loves to give check.")
      ),
      BotDescriptor(
        botId = "smarter-bot",
        displayName = "Smarter Bot",
        family = BotFamily.Heuristic,
        strategyType = "positional",
        engineType = "none",
        modelVersion = "1.0",
        description = Some("Uses piece-square tables for positional evaluation.")
      )
    )
    bots = defaultBots.map(b => b.botId -> b).toMap

  initializeDefaults()

  def listBots(): List[BotDescriptor] = bots.values.toList

  def getBot(botId: String): Option[BotDescriptor] = bots.get(botId)

  def registerBot(request: RegisterBotRequest): Either[String, BotDescriptor] =
    if request.botId.trim.isEmpty then
      Left("Bot ID cannot be empty")
    else if bots.contains(request.botId) then
      Left(s"Bot with ID ${request.botId} already exists")
    else
      BotFamily.fromString(request.family) match
        case None => Left(s"Invalid bot family: ${request.family}")
        case Some(family) =>
          val descriptor = BotDescriptor(
            botId = request.botId,
            displayName = request.displayName,
            family = family,
            strategyType = request.strategyType,
            engineType = request.engineType,
            modelVersion = request.modelVersion,
            author = request.author,
            description = request.description
          )
          bots = bots + (request.botId -> descriptor)
          Right(descriptor)

  def unregisterBot(botId: String): Either[String, Unit] =
    if bots.contains(botId) then
      bots = bots - botId
      Right(())
    else
      Left(s"Bot not found: $botId")

  def updateAvailability(botId: String, available: Boolean, reason: Option[String]): Either[String, BotDescriptor] =
    bots.get(botId) match
      case None => Left(s"Bot not found: $botId")
      case Some(descriptor) =>
        val updated = descriptor.copy(
          available = available,
          unavailableReason = if available then None else reason
        )
        bots = bots + (botId -> updated)
        Right(updated)

object BotRegistry:
  def create(): BotRegistry = new InMemoryBotRegistry()
