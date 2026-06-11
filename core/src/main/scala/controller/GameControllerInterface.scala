package controller

import model.{PositionState, TimeControl}
import java.nio.file.Path

trait GameControllerInterface {
  def create(whitePlayerName: String, blackPlayerName: String): PositionState
  def createWithTimeControl(whitePlayerName: String, blackPlayerName: String, timeControl: TimeControl): PositionState
  def save(state : PositionState, filePath: Path): Unit
  def load(filePath: Path): PositionState
  def makeMove(state: PositionState, move: String): Either[String, PositionState]
  def isGameOver(state: PositionState): Boolean
  def exportToPgn(state: PositionState, event: String, site: String, filename: String): Unit
}