package model

import scala.concurrent.duration._

/** Represents time control settings in milliseconds */
case class TimeControl(
  initialTimeMs: Long,      // Initial time in milliseconds
  incrementMs: Long = 0,    // Time added per move in milliseconds
  delayMs: Long = 0         // Delay before game starts
) {
  def withInitialTime(timeMs: Long): TimeControl = copy(initialTimeMs = timeMs)
  def withIncrement(incMs: Long): TimeControl = copy(incrementMs = incMs)
}

/** Represents the remaining time for a player */
case class PlayerTime(
  remainingTimeMs: Long,    // Remaining time in milliseconds
  lastUpdatedAt: Long = System.currentTimeMillis()  // When was time last updated
) {

  /** Get current remaining time accounting for elapsed time since last update */
  def getCurrentTime: Long = {
    val elapsed = System.currentTimeMillis() - lastUpdatedAt
    Math.max(0, remainingTimeMs - elapsed)
  }

  /** Add time (typically increment) */
  def addTime(timeMs: Long): PlayerTime =
    PlayerTime(remainingTimeMs + timeMs, System.currentTimeMillis())

  /** Use time */
  def useTime(timeMs: Long): PlayerTime =
    PlayerTime(Math.max(0, remainingTimeMs - timeMs), System.currentTimeMillis())

  /** Check if time is up */
  def isTimeOver: Boolean = getCurrentTime <= 0
}

object TimeControl {
  // Classical time controls
  val BULLET = TimeControl(initialTimeMs = 60 * 1000, incrementMs = 0)                    // 1 minute
  val BLITZ = TimeControl(initialTimeMs = 3 * 60 * 1000, incrementMs = 2 * 1000)           // 3+2
  val RAPID = TimeControl(initialTimeMs = 10 * 60 * 1000, incrementMs = 5 * 1000)          // 10+5
  val CLASSICAL = TimeControl(initialTimeMs = 90 * 60 * 1000, incrementMs = 30 * 1000)     // 90+30
  val UNLIMITED = TimeControl(initialTimeMs = Long.MaxValue, incrementMs = 0)
}

