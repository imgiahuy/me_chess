package usecase

import controller.GameControllerInterface
import repository.GameRepository
import model.TimeControl

/** Use case for creating new games.
  * Orchestrates game creation between GameController and GameRepository.
  */
class CreateGameUseCase(
  controller: GameControllerInterface,
  repo: GameRepository
) {

  /** Create a new game with default player names. */
  def create(): String = {
    val initialState = controller.create("White", "Black")
    repo.createGame(initialState)
  }

  /** Create a new game with specified player names. */
  def create(whitePlayer: String, blackPlayer: String): String = {
    val initialState = controller.create(whitePlayer, blackPlayer)
    repo.createGame(initialState)
  }

  /** Create a new game with specified player names and time control. */
  def createWithTimeControl(whitePlayer: String, blackPlayer: String, timeControl: TimeControl): String = {
    val initialState = controller.createWithTimeControl(whitePlayer, blackPlayer, timeControl)
    repo.createGame(initialState)
  }
}
