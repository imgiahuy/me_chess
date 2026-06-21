package tournament.model

import java.time.Instant
import java.util.UUID

/** Tournament formats supported by the service. */
enum TournamentFormat:
  case RoundRobin
  case Swiss
  case Arena

/** Status of a tournament. */
enum TournamentStatus:
  case Created
  case Open
  case Running
  case Finished
  case Cancelled

/** A participant in a tournament (player or bot). */
case class Participant(
  id: String,
  name: String,
  botType: Option[String] = None,
  initialRating: Double = 1500.0
)

/** A pairing of two participants for a game. */
case class Pairing(
  whiteId: String,
  blackId: String,
  round: Int,
  gameId: Option[String] = None,
  result: Option[GameResult] = None
)

/** Result of a single game within a tournament. */
enum GameResult:
  case WhiteWin
  case BlackWin
  case Draw

object GameResult:
  def fromString(value: String): Option[GameResult] = value.trim.toLowerCase match
    case "whitewin" | "white" | "1-0" => Some(WhiteWin)
    case "blackwin" | "black" | "0-1" => Some(BlackWin)
    case "draw" | "1/2-1/2" | "0.5-0.5" => Some(Draw)
    case _ => None

/** A round consists of multiple pairings. */
case class Round(
  number: Int,
  pairings: List[Pairing] = Nil,
  startedAt: Option[Instant] = None,
  finishedAt: Option[Instant] = None
)

/** Tournament configuration provided on creation. */
case class TournamentConfig(
  name: String,
  format: TournamentFormat,
  rounds: Int,
  gamesPerPairing: Int = 1,
  timeControlSeconds: Int = 300,
  incrementSeconds: Int = 3,
  description: Option[String] = None
)

/** Full tournament aggregate. */
case class Tournament(
  id: String,
  name: String,
  format: TournamentFormat,
  rounds: Int,
  gamesPerPairing: Int,
  timeControlSeconds: Int,
  incrementSeconds: Int,
  description: Option[String],
  status: TournamentStatus,
  participants: List[Participant] = Nil,
  roundsData: List[Round] = Nil,
  standings: List[Standing] = Nil,
  createdAt: Instant = Instant.now(),
  startedAt: Option[Instant] = None,
  finishedAt: Option[Instant] = None
)

object Tournament:
  def create(config: TournamentConfig): Tournament =
    Tournament(
      id = UUID.randomUUID().toString,
      name = config.name,
      format = config.format,
      rounds = config.rounds,
      gamesPerPairing = config.gamesPerPairing,
      timeControlSeconds = config.timeControlSeconds,
      incrementSeconds = config.incrementSeconds,
      description = config.description,
      status = TournamentStatus.Created
    )

/** Standings entry for a participant. */
case class Standing(
  participantId: String,
  name: String,
  played: Int = 0,
  wins: Int = 0,
  draws: Int = 0,
  losses: Int = 0,
  score: Double = 0.0,
  rating: Double = 1500.0
)

/** API request/response helpers. */
case class CreateTournamentRequest(
  name: String,
  format: String,
  rounds: Int,
  gamesPerPairing: Option[Int],
  timeControlSeconds: Option[Int],
  incrementSeconds: Option[Int],
  description: Option[String]
)

case class RegisterParticipantRequest(name: String, botType: Option[String])

case class ReportResultRequest(gameId: String, result: String)

case class TournamentSummary(
  id: String,
  name: String,
  format: String,
  status: String,
  participantCount: Int,
  rounds: Int,
  currentRound: Int,
  createdAt: Instant
)

case class TournamentCreatedEvent(tournamentId: String, name: String, format: String, timestamp: Instant)
case class TournamentStartedEvent(tournamentId: String, timestamp: Instant)
case class TournamentFinishedEvent(tournamentId: String, timestamp: Instant)
case class ParticipantRegisteredEvent(tournamentId: String, participantId: String, name: String)
case class GameResultReportedEvent(tournamentId: String, gameId: String, result: String)
