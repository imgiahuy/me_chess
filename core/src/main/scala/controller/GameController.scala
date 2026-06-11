package controller

import model.{PositionState, Color, TimeControl, Bot}
import parser.UciParser
import service.{GameService, BotService}

import java.nio.file.{Files, Paths}

class GameController extends GameControllerInterface {

  override def create(whitePlayerName: String, blackPlayerName: String): PositionState = {
    GameService.createGame(whitePlayerName, blackPlayerName, None)
  }

  def createWithTimeControl(whitePlayerName: String, blackPlayerName: String, timeControl: TimeControl): PositionState = {
    GameService.createGame(whitePlayerName, blackPlayerName, Some(timeControl))
  }
  
  override def save(state : PositionState, filePath: java.nio.file.Path): Unit = {
    val data = GameService.save(state)
    Files.write(filePath, data.getBytes)
    println(s"Game saved to $filePath")
  }

  override def load(filePath: java.nio.file.Path): PositionState = {
    val content = new String(Files.readAllBytes(filePath))
    val state = GameService.load(content)
    println(s"Game loaded from $filePath")
    state
  }

  override def makeMove(
    state: PositionState,
    input: String
  ): Either[String, PositionState] = {
    val cleanInput = input.trim.toLowerCase.replaceAll("[\\s\\-]+", "")
    
    UciParser.parse(cleanInput) match {
      case Right(m) =>
        GameService.applyMove(state, m)

      case Left(err) =>
        Left(s"Invalid move format: $err (use UCI notation like e2e4)")
    }
  }

  def isGameOver(state: PositionState): Boolean = GameService.isGameOver(state)

  override def exportToPgn(state: PositionState, event: String, site: String, filename: String): Unit = {
    val pgnData = GameService.exportToPgn(state, event, site)
    val path = Paths.get(filename)
    Files.write(path, pgnData.getBytes)
    println(s"Game exported to PGN file: $path")
  }

  // ── New features ────────────────────────────────────────────────────

  /** Player resigns from the game */
  def resign(state: PositionState, color: Color): PositionState = {
    GameService.resign(state, color)
  }

  /** Get remaining time for a player */
  def getRemainingTime(state: PositionState, color: Color): Option[Long] = {
    GameService.getRemainingTime(state, color)
  }

  /** Update time after a move */
  def updateTimeAfterMove(state: PositionState, timeSpentMs: Long, incrementMs: Long = 0): PositionState = {
    GameService.updateTimeAfterMove(state, timeSpentMs, incrementMs)
  }

  /** Get all legal moves for a position */
  def getLegalMoves(state: PositionState, color: Color): List[model.Move] = {
    GameService.getLegalMoves(state, color)
  }

  /** Play a bot move */
  def playBotMove(bot: Bot, state: PositionState): Either[String, PositionState] = {
    BotService.playBotMove(bot, state)
  }

  /** Create a bot by type name */
  def createBot(botType: String): Bot = {
    BotService.createBot(botType)
  }

  /** Get available bot types */
  def getAvailableBots: List[String] = {
    BotService.availableBots
  }
}


