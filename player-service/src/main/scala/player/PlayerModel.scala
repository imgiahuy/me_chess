package player

import java.time.Instant

/** Domain model for player management microservice. */

case class Player(
  id: String,
  username: String,
  email: Option[String],
  rating: Int,
  gamesPlayed: Int,
  wins: Int,
  losses: Int,
  draws: Int,
  createdAt: String,
  lastSeenAt: String
) {
  def winRate: Double = if (gamesPlayed == 0) 0.0 else wins.toDouble / gamesPlayed * 100
}

case class CreatePlayerRequest(username: String, email: Option[String] = None, initialRating: Int = 1200)

case class UpdatePlayerRequest(email: Option[String] = None, rating: Option[Int] = None)

case class RecordGameRequest(
  playerId: String,
  opponentId: String,
  result: String,
  ratingChange: Int
)

case class PlayerStats(
  playerId: String,
  username: String,
  rating: Int,
  gamesPlayed: Int,
  wins: Int,
  losses: Int,
  draws: Int,
  winRate: Double,
  rank: Int
)

case class PlayerListResponse(players: List[Player], total: Int)
case class PlayerStatsListResponse(stats: List[PlayerStats], generatedAt: String)
case class ActionResponse(message: String)
case class ErrorResponse(error: String)
