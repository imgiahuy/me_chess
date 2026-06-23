package usecase

import repository.GameRepository
import model.PositionState

/** Use case for retrieving game state from repository.
  * Provides methods to query games and game summaries.
  */
class GetGameUseCase(repo: GameRepository) {

  /** Get a game by ID. */
  def getGame(gameId: String): Option[PositionState] =
    repo.getGame(gameId)

  /** List all active game IDs. */
  def listGames(): List[String] =
    repo.listGames()

  /** Get lightweight summaries of all games without full state. */
  def getGameSummaries(): List[(String, String, Int, Boolean)] =
    repo.getGameSummaries()

  /** Load the latest game from the database and return its ID. */
  def loadLatestGame(): Either[String, String] =
    repo.loadLatestGame()
}
