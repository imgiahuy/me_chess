package controller

import model.PositionState

trait GameControllerInterface {
  def create(whitePlayerName: String, blackPlayerName: String): PositionState
  def save(state : PositionState): Unit
  def load(): PositionState
  def makeMove(state: PositionState, move: String): Either[String, PositionState]
  def isGameOver(state: PositionState): Boolean
  def exportToPgn(state: PositionState, event: String, site: String, filename: String): Unit
}