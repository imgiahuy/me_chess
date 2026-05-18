package controller

import model.Snapshot

trait GameControllerInterface {
  def create(): Snapshot
  def undo(): Unit
  def redo(): Unit
  def save(state : Snapshot): Unit
  def load(): Snapshot
  def makeMove(state: Snapshot, move: String): Either[String, Snapshot]
  def isGameOver(state: Snapshot): Boolean
}