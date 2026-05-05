package controller

import domain.engine.GameState
import domain.model.Move

trait GameControllerInterface {
  def create(): GameState
  def undo(): Unit
  def redo(): Unit
  def save(state : GameState): Unit
  def load(): GameState
  def makeMove(state: GameState, move: Move): Either[String, GameState]
}