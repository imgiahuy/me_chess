package model

case class Position(col: Int, row: Int) {

  /** Returns true only when both coordinates are inside the 8×8 board. */
  def isValid: Boolean = col >= 0 && col < 8 && row >= 0 && row < 8

}