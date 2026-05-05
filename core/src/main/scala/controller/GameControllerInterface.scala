package controller

trait GameControllerInterface {
  def create(): Unit
  def undo(): Unit
  def redo(): Unit
  def save(): Unit
  def load(): Unit
  def makeMove(from: String, to: String): Unit
  def get(): Unit
  def delete():Unit
}