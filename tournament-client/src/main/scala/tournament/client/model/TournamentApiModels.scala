package tournament.client.model

import java.time.Instant
import upickle.default.{ReadWriter, macroRW}

/** Models for external Tournament Server API integration */

// --- Authentication ---

case class RegisterRequest(
  name: String,
  isBot: Boolean
)

case class RegisterResponse(
  id: String,
  token: String
)

// --- Tournament ---

case class TournamentListResponse(
  created: List[TournamentSummary],
  started: List[TournamentSummary],
  finished: List[TournamentSummary]
)

case class TournamentSummary(
  id: String,
  name: String,
  status: String,
  format: String,
  rounds: Int,
  currentRound: Int,
  participantCount: Int,
  createdAt: Instant
)

case class TournamentDetail(
  id: String,
  name: String,
  status: String,
  format: String,
  rounds: Int,
  gamesPerPairing: Int,
  timeControlSeconds: Int,
  incrementSeconds: Int,
  participants: List[ParticipantInfo],
  standings: List[StandingInfo],
  createdAt: Instant,
  startedAt: Option[Instant],
  finishedAt: Option[Instant]
)

case class ParticipantInfo(
  id: String,
  name: String,
  botType: Option[String],
  rating: Double
)

case class StandingInfo(
  participantId: String,
  name: String,
  score: Double,
  wins: Int,
  draws: Int,
  losses: Int,
  rating: Double
)

// --- Tournament Events (NDJSON) ---

sealed trait TournamentEvent
case class TournamentStartedEvent(
  `type`: String = "tournamentStarted",
  tournamentId: String,
  timestamp: Instant
) extends TournamentEvent
case class RoundStartedEvent(
  `type`: String = "roundStarted",
  tournamentId: String,
  round: Int,
  timestamp: Instant
) extends TournamentEvent
case class GameStartEvent(
  `type`: String = "gameStart",
  tournamentId: String,
  round: Int,
  gameId: String,
  color: String, // "white" or "black"
  opponentId: String,
  opponentName: String,
  timestamp: Instant
) extends TournamentEvent
case class RoundFinishedEvent(
  `type`: String = "roundFinished",
  tournamentId: String,
  round: Int,
  timestamp: Instant
) extends TournamentEvent
case class TournamentFinishedEvent(
  `type`: String = "tournamentFinished",
  tournamentId: String,
  timestamp: Instant
) extends TournamentEvent
case class HeartbeatEvent(
  `type`: String = "heartbeat",
  timestamp: Instant
) extends TournamentEvent

// --- Game Events (NDJSON) ---

sealed trait GameEvent
case class GameStateEvent(
  `type`: String = "gameState",
  tournamentId: String,
  gameId: String,
  fen: String,
  turn: String, // "white" or "black"
  whiteTimeMs: Long,
  blackTimeMs: Long,
  timestamp: Instant
) extends GameEvent
case class MoveEvent(
  `type`: String = "move",
  tournamentId: String,
  gameId: String,
  uci: String,
  fen: String,
  turn: String,
  timestamp: Instant
) extends GameEvent
case class GameEndEvent(
  `type`: String = "gameEnd",
  tournamentId: String,
  gameId: String,
  result: String, // "whiteWin", "blackWin", "draw"
  reason: String,
  timestamp: Instant
) extends GameEvent
case class GameHeartbeatEvent(
  `type`: String = "heartbeat",
  timestamp: Instant
) extends GameEvent

// --- Bot Registration ---

case class RegisterBotRequest(
  name: String,
  family: String,
  strategyType: String,
  engineType: String,
  modelVersion: String
)

case class RegisterBotResponse(
  botId: String
)

case class AddParticipantRequest(
  botId: String
)

// --- Error Response ---

case class ErrorResponse(
  error: String,
  details: Option[String] = None
)

// --- uPickle JSON Readers/Writers ---

object JsonFormats {
  given ReadWriter[Instant] = upickle.default.readwriter[String].bimap(
    i => i.toString,
    s => Instant.parse(s)
  )
  
  given ReadWriter[RegisterRequest] = macroRW
  given ReadWriter[RegisterResponse] = macroRW
  given ReadWriter[TournamentSummary] = macroRW
  given ReadWriter[TournamentDetail] = macroRW
  given ReadWriter[TournamentListResponse] = macroRW
  given ReadWriter[ParticipantInfo] = macroRW
  given ReadWriter[StandingInfo] = macroRW
  given ReadWriter[TournamentStartedEvent] = macroRW
  given ReadWriter[RoundStartedEvent] = macroRW
  given ReadWriter[GameStartEvent] = macroRW
  given ReadWriter[RoundFinishedEvent] = macroRW
  given ReadWriter[TournamentFinishedEvent] = macroRW
  given ReadWriter[HeartbeatEvent] = macroRW
  given ReadWriter[GameStateEvent] = macroRW
  given ReadWriter[MoveEvent] = macroRW
  given ReadWriter[GameEndEvent] = macroRW
  given ReadWriter[GameHeartbeatEvent] = macroRW
  given ReadWriter[RegisterBotRequest] = macroRW
  given ReadWriter[RegisterBotResponse] = macroRW
  given ReadWriter[AddParticipantRequest] = macroRW
  given ReadWriter[ErrorResponse] = macroRW
}
