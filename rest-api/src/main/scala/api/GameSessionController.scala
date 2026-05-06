package api

import controller.GameControllerInterface
import model.Snapshot
import repository.GameRepository

class GameSessionController(
                             controller: GameControllerInterface,
                             repo: GameRepository
                           ) {

  /** Create a new game session and return its ID */
  def createGame(): String = {
    val initialState = controller.create()
    repo.createGame(initialState)
  }

  /** Get a game by ID */
  def getGame(gameId: String): Option[Snapshot] =
    repo.getGame(gameId)

  /** Apply a move using gameId */
  def makeMove(gameId: String, input: String): Either[String, Snapshot] = {
    for {
      state    <- repo.getGame(gameId).toRight("Game not found")
      newState <- controller.makeMove(state, input)
    } yield {
      repo.updateGame(gameId, newState)
      newState
    }
  }

  /** Delete a game session */
  def deleteGame(gameId: String): Boolean =
    repo.deleteGame(gameId)

  /** List all active games */
  def listGames(): List[String] =
    repo.listGames()

  /** Save a specific game */
  def saveGame(gameId: String): Either[String, Unit] = {
    repo.getGame(gameId) match {
      case Some(state) =>
        controller.save(state)
        Right(())
      case None =>
        Left("Game not found")
    }
  }

  /** Load a game and create a new session for it */
  def loadGame(): String = {
    val state = controller.load()
    repo.createGame(state)
  }

  def isGameOver(state: Snapshot): Nothing = ???

  def updateGame(gameId: String, newState: Snapshot): Unit = repo.updateGame(gameId, newState)

  def winner(state: Snapshot): Nothing = ???
}