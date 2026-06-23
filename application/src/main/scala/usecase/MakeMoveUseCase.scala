package usecase

import controller.GameControllerInterface
import repository.GameRepository
import model.PositionState
import scala.concurrent.{ExecutionContext, Future}

/** Use case for making moves in a game.
  * Orchestrates move execution between GameController and GameRepository.
  */
class MakeMoveUseCase(
  controller: GameControllerInterface,
  repo: GameRepository
)(implicit ec: ExecutionContext) {

  /** Apply a move to a game by ID. */
  def makeMove(gameId: String, input: String): Either[String, PositionState] = {
    if (input.trim.isEmpty) {
      Left("Move input cannot be empty")
    } else {
      for {
        state    <- repo.getGame(gameId).toRight("Game not found")
        newState <- controller.makeMove(state, input)
      } yield {
        repo.updateGame(gameId, newState)
        newState
      }
    }
  }
}
