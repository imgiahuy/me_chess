package model

/** Represents the result of a game */
sealed trait GameResult
case object Ongoing extends GameResult
case class Checkmate(winner: Color) extends GameResult
case class Draw(reason: DrawReason) extends GameResult
case class Resignation(winner: Color) extends GameResult  // Winner is the player who didn't resign
case class TimeOut(winner: Color) extends GameResult      // Winner is the player with remaining time

