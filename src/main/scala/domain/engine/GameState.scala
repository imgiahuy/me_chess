package domain.engine

import domain.model.*

import scala.util.{Try}

/** The complete, immutable state of a chess game at one point in time. */
case class GameState(
                      board: Board,
                      currentTurn: Color,
                      moveHistory: List[Move]
                    ) {

  /** Applies `move` and advances the turn; returns Success(newState) or Failure. */
  def applyMove(move: Move): Try[GameState] = for {
    newBoard <- board.withMove(move.from, move.to) // Try[Board]
  } yield GameState(
    board       = newBoard,
    currentTurn = currentTurn.opposite,
    moveHistory = move :: moveHistory
  )

  /** The most recent move, or None if the game just started. */
  def lastMove: Option[Move] = moveHistory.headOption

  /** Total half-moves (plies) played. */
  def totalMoves: Int = moveHistory.size
}

object GameState {
  /** Standard chess starting position with White to move. */
  def initial: GameState = GameState(Board.initial, White, List.empty)
}