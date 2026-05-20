package controller

import model.PositionState

trait GameControllerInterface {
  def create(): PositionState
  def save(state : PositionState): Unit
  def load(): PositionState
  def makeMove(state: PositionState, move: String): Either[String, PositionState]
  def isGameOver(state: PositionState): Boolean
}