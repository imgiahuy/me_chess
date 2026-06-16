package kafka

import model.{Color, GameResult, Move, PositionState}
import java.time.Instant
import java.util.UUID

/** Base trait for all chess game events streamed through Kafka.
  * Events represent state changes in chess games that can be
  * consumed by microservices for real-time processing.
  */
sealed trait GameEvent {
  def eventId: String
  def gameId: String
  def timestamp: Instant
  def eventType: String
}

/** Event fired when a new game is created.
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the event occurred
  * @param whitePlayer Name of the white player
  * @param blackPlayer Name of the black player
  * @param initialFen Initial board position in FEN notation
  */
case class GameCreatedEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  whitePlayer: String,
  blackPlayer: String,
  initialFen: String,
  timeControl: Option[String] = None
) extends GameEvent {
  def eventType: String = "GAME_CREATED"
}

/** Event fired when a move is made in a game.
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the move occurred
  * @param playerColor The color of the player who made the move
  * @param moveUCI The move in UCI notation (e.g., "e2e4")
  * @param moveNumber The move number in the game
  * @param fenAfterMove Board position after the move in FEN notation
  * @param remainingWhiteTime Remaining time for white in milliseconds (if timed)
  * @param remainingBlackTime Remaining time for black in milliseconds (if timed)
  */
case class MoveMadeEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  playerColor: String,
  moveUCI: String,
  moveNumber: Int,
  fenAfterMove: String,
  remainingWhiteTime: Option[Long] = None,
  remainingBlackTime: Option[Long] = None
) extends GameEvent {
  def eventType: String = "MOVE_MADE"
}

/** Event fired when a game ends.
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the game ended
  * @param result The game result (checkmate, draw, resignation, timeout)
  * @param winner The winning color (if applicable)
  * @param reason Additional reason for the result
  * @param finalFen Final board position in FEN notation
  * @param totalMoves Total number of moves played
  */
case class GameEndedEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  result: String,  // "checkmate", "draw", "resignation", "timeout"
  winner: Option[String],  // "white", "black", or None for draw
  reason: Option[String],  // e.g., "stalemate", "insufficient material"
  finalFen: String,
  totalMoves: Int,
  pgn: Option[String] = None
) extends GameEvent {
  def eventType: String = "GAME_ENDED"
}

/** Event fired when a player resigns.
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the resignation occurred
  * @param resigningColor The color of the player who resigned
  */
case class PlayerResignedEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  resigningColor: String
) extends GameEvent {
  def eventType: String = "PLAYER_RESIGNED"
  def winner: String = if (resigningColor == "white") "black" else "white"
}

/** Event fired when a draw is offered (for future draw acceptance flow).
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the draw was offered
  * @param offeringColor The color of the player offering draw
  */
case class DrawOfferedEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  offeringColor: String
) extends GameEvent {
  def eventType: String = "DRAW_OFFERED"
}

/** Event fired for time warnings or timeouts.
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the event occurred
  * @param playerColor The color of the player with time warning
  * @param remainingTimeMs Remaining time in milliseconds
  * @param isTimeout Whether this is a timeout event
  */
case class TimeWarningEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  playerColor: String,
  remainingTimeMs: Long,
  isTimeout: Boolean = false
) extends GameEvent {
  def eventType: String = if (isTimeout) "TIMEOUT" else "TIME_WARNING"
}

/** Event fired when a game state is updated (e.g., after bot move).
  *
  * @param eventId Unique identifier for this event
  * @param gameId The game identifier
  * @param timestamp When the update occurred
  * @param fen Current board position in FEN notation
  * @param turn Current turn color
  * @param moveHistory List of moves in UCI notation
  * @param isGameOver Whether the game has ended
  */
case class GameStateUpdatedEvent(
  eventId: String = UUID.randomUUID().toString,
  gameId: String,
  timestamp: Instant = Instant.now(),
  fen: String,
  turn: String,
  moveHistory: List[String],
  isGameOver: Boolean
) extends GameEvent {
  def eventType: String = "GAME_STATE_UPDATED"
}

/** Companion object with helper methods for creating events from game state. */
object GameEvent {

  /** Creates a GameCreatedEvent from initial game parameters. */
  def gameCreated(
    gameId: String,
    whitePlayer: String,
    blackPlayer: String,
    initialFen: String,
    timeControl: Option[String] = None
  ): GameCreatedEvent = GameCreatedEvent(
    gameId = gameId,
    whitePlayer = whitePlayer,
    blackPlayer = blackPlayer,
    initialFen = initialFen,
    timeControl = timeControl
  )

  /** Creates a MoveMadeEvent from a PositionState after a move. */
  def moveMade(
    gameId: String,
    state: PositionState,
    moveUCI: String,
    moveNumber: Int
  ): MoveMadeEvent = MoveMadeEvent(
    gameId = gameId,
    playerColor = state.turn match {
      case model.White => "black"  // Turn already switched after move
      case model.Black => "white"
    },
    moveUCI = moveUCI,
    moveNumber = moveNumber,
    fenAfterMove = formatter.FenFormatter.fenFormatter(state),
    remainingWhiteTime = state.whiteTime.map(_.getCurrentTime),
    remainingBlackTime = state.blackTime.map(_.getCurrentTime)
  )

  /** Creates a GameEndedEvent from a final PositionState. */
  def gameEnded(
    gameId: String,
    state: PositionState,
    pgn: Option[String] = None
  ): GameEndedEvent = {
    val (result, winner, reason) = state.gameResult match {
      case model.Checkmate(w) => ("checkmate", Some(w.toString.toLowerCase), None)
      case model.Draw(r) => ("draw", None, Some(r.toString))
      case model.Resignation(w) => ("resignation", Some(w.toString.toLowerCase), None)
      case model.TimeOut(w) => ("timeout", Some(w.toString.toLowerCase), None)
      case model.Ongoing => ("ongoing", None, None)
    }

    GameEndedEvent(
      gameId = gameId,
      result = result,
      winner = winner,
      reason = reason,
      finalFen = formatter.FenFormatter.fenFormatter(state),
      totalMoves = state.moveHistory.length,
      pgn = pgn
    )
  }

  /** Creates a PlayerResignedEvent. */
  def playerResigned(gameId: String, color: String): PlayerResignedEvent =
    PlayerResignedEvent(gameId = gameId, resigningColor = color)

  /** Creates a TimeWarningEvent. */
  def timeWarning(
    gameId: String,
    playerColor: String,
    remainingTimeMs: Long,
    isTimeout: Boolean = false
  ): TimeWarningEvent = TimeWarningEvent(
    gameId = gameId,
    playerColor = playerColor,
    remainingTimeMs = remainingTimeMs,
    isTimeout = isTimeout
  )

  /** Creates a GameStateUpdatedEvent from current PositionState. */
  def gameStateUpdated(
    gameId: String,
    state: PositionState
  ): GameStateUpdatedEvent = GameStateUpdatedEvent(
    gameId = gameId,
    fen = formatter.FenFormatter.fenFormatter(state),
    turn = state.turn.toString.toLowerCase,
    moveHistory = state.moveHistory.zipWithIndex.map { case (m, i) =>
      val moveNum = i + 1
      val uci = formatter.UciFormatter.moveToUci(m)
      s"$moveNum.$uci"
    },
    isGameOver = state.gameResult != model.Ongoing
  )
}
