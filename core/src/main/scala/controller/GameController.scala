package controller

import model.Snapshot
import parser.MoveParser
import service.GameService

import java.nio.file.{Files, Paths}

class GameController extends GameControllerInterface {

  override def create(): Snapshot = {
    GameService.createGame()
  }

  //Todo: Implement undo/redo functionality by maintaining a history stack of GameStates, and a pointer to the current state. Undo moves the pointer back, redo moves it forward.
  // Using Observer pattern, we can notify the GUI and TUI to update whenever the state changes due to undo/redo.
  override def undo(): Unit = {

  }

  override def redo(): Unit = {

  }

  override def save(state : Snapshot): Unit = {
    val data = GameService.save(state)
    val path = Paths.get("savegame.txt")
    Files.write(path, data.getBytes)
    println(s"Game saved to $path")
  }

  override def load(): Snapshot = {
    val path = Paths.get("savegame.txt")
    val content = new String(Files.readAllBytes(path))
    val state = GameService.load(content)
    println(s"Game loaded from $path")
    state
  }

  override def makeMove(
                state: Snapshot,
                input: String
              ): Either[String, Snapshot] = {

    for {
      move     <- MoveParser.parse(input).toRight("Invalid move format")
      newState <- GameService.applyMove(state, move)
    } yield newState
  }
}
