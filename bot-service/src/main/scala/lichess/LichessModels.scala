package lichess

import java.time.Instant

/** Domain models for the Lichess Bot API (https://lichess.org/api#tag/Bot).
  *
  * Only the fields required to accept challenges and play games are modelled.
  */

/** Top-level event received on the account event stream (NDJSON). */
case class LichessEvent(
  `type`: String,
  challenge: Option[Challenge] = None,
  game: Option[GameInfo] = None
)

/** A challenge issued to or by the bot account. */
case class Challenge(
  id: String,
  status: String,
  rated: Boolean,
  speed: String,
  timeControl: Option[TimeControl],
  variant: Variant,
  challenger: PlayerRef,
  destUser: Option[PlayerRef]
)

/** Time control of a Lichess challenge. */
case class TimeControl(
  `type`: String,
  limit: Option[Int] = None,
  increment: Option[Int] = None,
  daysPerTurn: Option[Int] = None
)

/** Lichess variant (standard, chess960, etc.). */
case class Variant(key: String, name: String)

/** Minimal player reference used in challenges and game streams. */
case class PlayerRef(
  id: Option[String] = None,
  name: Option[String] = None,
  rating: Option[Int] = None
)

/** Game reference from the account event stream. */
case class GameInfo(
  id: String,
  color: Option[String] = None,
  speed: Option[String] = None,
  rated: Option[Boolean] = None
)

/** Sealed trait for events received on a single game stream. */
sealed trait GameStreamEvent {
  def `type`: String
}

/** Initial game state when a game stream starts. */
case class GameFull(
  id: String,
  initialFen: Option[String],
  color: String,
  opponent: Option[PlayerRef],
  state: GameState
) extends GameStreamEvent {
  override val `type`: String = "gameFull"
}

/** Incremental game state update received during a game stream. */
case class GameState(
  moves: String,
  wtime: Option[Int],
  btime: Option[Int],
  winc: Option[Int],
  binc: Option[Int],
  status: Option[String],
  winner: Option[String],
  draw: Option[Boolean] = None
) extends GameStreamEvent {
  override val `type`: String = "gameState"
}

/** Unknown event type received on game stream (parse error or unsupported event). */
case class UnknownEvent(raw: String) extends GameStreamEvent {
  override val `type`: String = "unknown"
}

/** Configuration that controls which challenges the bot accepts. */
case class ChallengeFilter(
  allowRated: Boolean = true,
  allowUnrated: Boolean = true,
  allowedSpeeds: Set[String] = Set("bullet", "blitz", "rapid", "classical"),
  allowedVariants: Set[String] = Set("standard"),
  maxInitialTimeSeconds: Int = Int.MaxValue,
  minIncrementSeconds: Int = 0
)

/** Configuration for the bot worker. */
case class LichessBotConfig(
  apiToken: String,
  baseUrl: String = "https://lichess.org",
  botType: String = "greedy",
  challengeFilter: ChallengeFilter = ChallengeFilter(),
  requestTimeoutMs: Int = 10000
)

/** Result of a finished game as reported by Lichess. */
case class GameResult(
  gameId: String,
  status: String,
  winner: Option[String],
  draw: Boolean
)

/** Internal game tracking record used by the bot worker. */
case class ActiveGame(
  gameId: String,
  myColor: model.Color,
  opponent: Option[PlayerRef],
  state: model.PositionState,
  bot: model.Bot,
  lastMoves: String = ""
)
