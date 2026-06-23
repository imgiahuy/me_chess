package tournament.client.model

import java.time.Instant
import upickle.default.{ReadWriter, macroRW}

/** Models for external Tournament Server API integration - matches OpenAPI spec */

// --- Authentication ---

case class RegisterRequest(
  name: String,
  isBot: Boolean = false
)

case class RegisterResponse(
  id: String,
  token: String
)

// --- Tournament ---

case class TournamentListResponse(
  created: List[TournamentInfo],
  started: List[TournamentInfo],
  finished: List[TournamentInfo]
)

case class TournamentInfo(
  id: String,
  fullName: String,
  clock: Clock,
  variant: Variant,
  rated: Boolean,
  nbPlayers: Int,
  nbRounds: Int,
  format: String,
  matchesPerPairing: Int,
  startPosition: String,
  createdBy: String,
  startsAt: String
)

case class Clock(
  limit: Int,
  increment: Int
)

case class Variant(
  key: String,
  name: String
)

case class TournamentDetail(
  id: String,
  fullName: String,
  clock: Clock,
  variant: Variant,
  rated: Boolean,
  nbPlayers: Int,
  nbRounds: Int,
  format: String,
  matchesPerPairing: Int,
  startPosition: String,
  createdBy: String,
  startsAt: String,
  status: String,
  round: Int,
  standing: Standing,
  winner: Option[BotRef]
)

case class Standing(
  page: Int,
  players: List[Result]
)

case class Result(
  rank: Int,
  points: Double,
  tieBreak: Double,
  bot: BotRef,
  nbGames: Int,
  wins: Int,
  draws: Int,
  losses: Int
)

case class BotRef(
  id: String,
  name: String
)

// --- Tournament Events (NDJSON) ---

case class TournamentEvent(
  `type`: String,
  round: Option[Int] = None,
  gameId: Option[String] = None,
  color: Option[String] = None,
  winner: Option[BotRef] = None
)

// --- Game Events (NDJSON) ---

case class GameEvent(
  `type`: String,
  uci: Option[String] = None,
  fen: Option[String] = None,
  turn: Option[String] = None,
  clock: Option[GameClock] = None,
  winner: Option[String] = None,
  status: Option[String] = None
)

case class GameClock(
  whiteTime: Double,
  blackTime: Double,
  increment: Int
)

// --- Game State ---

case class GameState(
  id: String,
  tournamentId: String,
  round: Int,
  white: BotRef,
  black: BotRef,
  moves: String,
  fen: String,
  status: String,
  turn: String,
  winner: Option[String],
  clock: GameClock,
  startPosition: String
)

// --- Bot Registration ---

case class RegisterBotRequest(
  name: String,
  endpoint: Option[String] = None,
  family: Option[String] = None,
  strategyType: Option[String] = None,
  engineType: Option[String] = None,
  modelVersion: Option[String] = None
)

case class RegisteredBot(
  id: String,
  name: String,
  endpoint: Option[String],
  family: Option[String],
  strategyType: Option[String],
  engineType: Option[String],
  modelVersion: Option[String]
)

case class AddParticipantRequest(
  botId: String
)

// --- Create Tournament Form ---

case class CreateTournamentForm(
  name: String,
  nbRounds: Int,
  clockLimit: Int,
  clockIncrement: Int,
  rated: Boolean = true,
  format: String = "swiss",
  startPosition: String = "standard",
  matchesPerPairing: Int = 1,
  groupSize: Option[Int] = None,
  opening: Option[String] = None,
  bots: Option[String] = None,
  maxConcurrentGames: Option[Int] = None,
  openings: Option[String] = None
)

// --- Error Response ---

case class ErrorResponse(
  error: String,
  details: Option[String] = None
)

// --- Simple Response ---

case class Ok(
  ok: Boolean = true
)

// --- uPickle JSON Readers/Writers ---

object JsonFormats {
  given ReadWriter[Instant] = upickle.default.readwriter[String].bimap(
    i => i.toString,
    s => Instant.parse(s)
  )
  
  given ReadWriter[RegisterRequest] = macroRW
  given ReadWriter[RegisterResponse] = macroRW
  given ReadWriter[TournamentListResponse] = macroRW
  given ReadWriter[TournamentInfo] = macroRW
  given ReadWriter[Clock] = macroRW
  given ReadWriter[Variant] = macroRW
  given ReadWriter[TournamentDetail] = macroRW
  given ReadWriter[Standing] = macroRW
  given ReadWriter[Result] = macroRW
  given ReadWriter[BotRef] = macroRW
  given ReadWriter[TournamentEvent] = macroRW
  given ReadWriter[GameEvent] = macroRW
  given ReadWriter[GameClock] = macroRW
  given ReadWriter[GameState] = macroRW
  given ReadWriter[RegisterBotRequest] = macroRW
  given ReadWriter[RegisteredBot] = macroRW
  given ReadWriter[AddParticipantRequest] = macroRW
  given ReadWriter[CreateTournamentForm] = macroRW
  given ReadWriter[ErrorResponse] = macroRW
  given ReadWriter[Ok] = macroRW
}
