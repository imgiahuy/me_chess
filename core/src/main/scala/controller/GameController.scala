package controller

import model.PositionState
import parser.UciParser
import service.GameService

import java.nio.file.{Files, Paths}

class GameController extends GameControllerInterface {

  override def create(): PositionState = {
    GameService.createGame()
  }
  
  override def save(state : PositionState): Unit = {
    val data = GameService.save(state)
    val path = Paths.get("savegame.txt")
    Files.write(path, data.getBytes)
    println(s"Game saved to $path")
  }

  override def load(): PositionState = {
    val path = Paths.get("savegame.txt")
    val content = new String(Files.readAllBytes(path))
    val state = GameService.load(content)
    println(s"Game loaded from $path")
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
}
