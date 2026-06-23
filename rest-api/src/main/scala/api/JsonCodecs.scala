package api

import api.JsonCodecs.GameResultInfo
import model.{PositionState, TimeControl, PlayerTime, GameResult}
import service.GameService
import formatter.UciFormatter
import upickle.default.{ReadWriter, macroRW, readwriter}
import ujson.Value

object JsonCodecs {

  final case class MoveRequest(from: String, to: String, promotion: String = null, castling: String = null) {
    def toUci: String = {
      val fromTrimmed = from.trim.toLowerCase
      val toTrimmed = to.trim.toLowerCase

      // Handle castling: emit king-square notation that UciParser recognises as castling
      // (e1g1/e1c1 for White, e8g8/e8c8 for Black)
      Option(castling) match {
        case Some(c) if c == "kingside" || c == "0-0" || c == "short" =>
          // If the from/to already form a recognised castling pair, use them directly;
          // otherwise fall back to the known king-square patterns based on rank.
          val pair = s"${fromTrimmed}${toTrimmed}"
          if (pair == "e1g1" || pair == "e8g8") pair
          else if (fromTrimmed.startsWith("e") && fromTrimmed.endsWith("1")) "e1g1"
          else "e8g8"
        case Some(c) if c == "queenside" || c == "0-0-0" || c == "long" =>
          val pair = s"${fromTrimmed}${toTrimmed}"
          if (pair == "e1c1" || pair == "e8c8") pair
          else if (fromTrimmed.startsWith("e") && fromTrimmed.endsWith("1")) "e1c1"
          else "e8c8"
        case _ =>
          val promoTrimmed = Option(promotion).map(_.trim.toLowerCase).filter(_.nonEmpty)
          promoTrimmed match {
            case Some(promo) => s"$fromTrimmed$toTrimmed$promo"
            case None        => s"$fromTrimmed$toTrimmed"
          }
      }
    }
    
    def validate: Either[String, Unit] = {
      if (from.trim.isEmpty) Left("'from' field cannot be empty")
      else if (to.trim.isEmpty) Left("'to' field cannot be empty")
      else if (from.trim.length != 2) Left("'from' must be a valid square (e.g., 'e2')")
      else if (to.trim.length != 2) Left("'to' must be a valid square (e.g., 'e4')")
      else Right(())
    }
  }

  final case class CreatedGameResponse(gameId: String, message: String)

  final case class TimeControlInfo(
    initialTimeMs: Long,
    incrementMs: Long,
    remainingTimeMs: Long,
    delayMs: Long = 0
  )

  final case class GameResultInfo(
    status: String,  // "ongoing", "checkmate", "draw", "resignation", "timeout"
    reason: Option[String],  // e.g., "stalemate", "insufficient material", etc.
    winner: Option[String]
  )

  final case class GameStateResponse(
    gameId: String,
    fen: String,
    turn: String,
    moveHistory: List[String],
    gameResult: GameResultInfo,
    whiteTime: Option[TimeControlInfo],
    blackTime: Option[TimeControlInfo],
    legalMoves: Option[List[String]],
    isPaused: Boolean = false
  )

  object GameStateResponse {
    /** Create GameStateResponse from PositionState */
    def fromPositionState(gameId: String, state: PositionState, includeLegalMoves: Boolean = false): GameStateResponse = {
      val gameResultInfo = state.gameResult match {
        case model.Ongoing => GameResultInfo("ongoing", None, None)
        case model.Checkmate(winner) => GameResultInfo("checkmate", None, Some(winner.toString))
        case model.Draw(reason) => GameResultInfo("draw", Some(reason.toString), None)
        case model.Resignation(winner) => GameResultInfo("resignation", None, Some(winner.toString))
        case model.TimeOut(winner) => GameResultInfo("timeout", None, Some(winner.toString))
      }

      val isUnlimited = state.timeControl.isEmpty || state.timeControl.exists(_.initialTimeMs == Long.MaxValue)

      val whiteTimeInfo = if (isUnlimited) None else state.whiteTime.map { pt =>
        val initial = state.timeControl.map(_.initialTimeMs).getOrElse(0L)
        val increment = state.timeControl.map(_.incrementMs).getOrElse(0L)
        val delay = state.timeControl.map(_.delayMs).getOrElse(0L)
        val isWhiteActive = state.turn == model.White
        val remaining = if (state.isPaused || !isWhiteActive) pt.remainingTimeMs else pt.getCurrentTime
        TimeControlInfo(initial, increment, remaining, delay)
      }

      val blackTimeInfo = if (isUnlimited) None else state.blackTime.map { pt =>
        val initial = state.timeControl.map(_.initialTimeMs).getOrElse(0L)
        val increment = state.timeControl.map(_.incrementMs).getOrElse(0L)
        val delay = state.timeControl.map(_.delayMs).getOrElse(0L)
        val isBlackActive = state.turn == model.Black
        val remaining = if (state.isPaused || !isBlackActive) pt.remainingTimeMs else pt.getCurrentTime
        TimeControlInfo(initial, increment, remaining, delay)
      }

      val legalMovesStr = if (includeLegalMoves) {
        Some(GameService.getLegalMoves(state, state.turn).map { m =>
          formatter.UciFormatter.moveToUci(m)
        })
      } else {
        None
      }

      GameStateResponse(
        gameId = gameId,
        fen = formatter.FenFormatter.fenFormatter(state),
        turn = state.turn.toString,
        moveHistory = UciFormatter.uciListFormatter(state.moveHistory).split("\\s+").toList.filter(_.nonEmpty),
        gameResult = gameResultInfo,
        whiteTime = whiteTimeInfo,
        blackTime = blackTimeInfo,
        legalMoves = legalMovesStr,
        isPaused = state.isPaused
      )
    }
  }

  final case class GameSummary(
    gameId: String,
    turn: String,
    gameResult: String,
    moveCount: Int
  )

  final case class GamesListResponse(games: List[GameSummary], total: Int)

  final case class ActionResponse(message: String)

  final case class ErrorResponse(error: String)

  final case class GameInfos(name: String, version: String, status: String)
  
  final case class GameStatusResponse(
    gameId: String,
    gameResult: GameResultInfo,
    turn: String,
    moveCount: Int,
    whiteTime: Option[TimeControlInfo],
    blackTime: Option[TimeControlInfo]
  )
  
  final case class CreateGameRequest(whitePlayer: String, blackPlayer: String, timeControl: String = null)

  final case class ExportRequest(event: String, site: String)

  final case class ExportResponse(
    gameId: String,
    pgnContent: String,
    filename: String
  )

  final case class ApiMetadata(
    version: String,
    endpoints: Int,
    features: List[String]
  )

  final case class ResignRequest(color: String)

  final case class BotMoveRequest(botType: String)

  final case class BotInfoResponse(id: String, name: String, difficulty: String, description: String)

  final case class AvailableBotsResponse(bots: List[BotInfoResponse])

  final case class LeaderboardEntry(
    rank: Int,
    player: String,
    totalGames: Long,
    victories: Long,
    defeats: Long,
    draws: Long,
    winRate: Double
  )

  final case class LeaderboardResponse(
    entries: List[LeaderboardEntry],
    generatedAt: String,
    source: String
  )

  final case class HealthStatus(
    status: String,
    service: String,
    version: String,
    activeGames: Int,
    timestamp: String
  )

  final case class MetricsResponse(
    activeGames: Int,
    heapUsedMb: Long,
    heapMaxMb: Long,
    uptimeSeconds: Long,
    timestamp: String
  )

  final case class AnalyticsExportRequest(
    format: String,  // "csv", "json", "pgn"
    startDate: Option[String] = None,
    endDate: Option[String] = None,
    includeMoves: Boolean = true,
    includeTimeControl: Boolean = true
  )

  final case class AnalyticsExportResponse(
    exportId: String,
    format: String,
    recordCount: Int,
    downloadUrl: String,
    expiresAt: String
  )

  final case class GameAnalytics(
    gameId: String,
    whitePlayer: String,
    blackPlayer: String,
    result: String,
    moveCount: Int,
    timeControl: Option[String],
    createdAt: String,
    durationSeconds: Option[Long],
    averageMoveTime: Option[Double],
    blunders: Int = 0,
    mistakes: Int = 0,
    inaccuracies: Int = 0
  )

  final case class OpeningInfo(
    name: String,
    ecoCode: String,
    moves: List[String],
    description: String,
    category: String
  )

  final case class OpeningMatchInfo(
    opening: OpeningInfo,
    matchedMoves: Int,
    confidence: Double
  )

  final case class OpeningsListResponse(openings: List[OpeningInfo], total: Int)

  final case class OpeningLookupRequest(moves: List[String])

  final case class OpeningSearchRequest(query: String)

  final case class OpeningCategoryResponse(category: String, openings: List[OpeningInfo])

  // Engine management types
  final case class EngineInfo(
    name: String,
    enginePath: String,
    options: Map[String, String],
    defaultDepth: Int,
    defaultTimeMs: Long,
    isRunning: Boolean
  )

  final case class EngineConfigResponse(
    name: String,
    enginePath: String,
    options: Map[String, String],
    defaultDepth: Int,
    defaultTimeMs: Long
  )

  final case class EngineConfigRequest(
    enginePath: String,
    options: Map[String, String] = Map.empty,
    defaultDepth: Int = 15,
    defaultTimeMs: Long = 1000
  )

  final case class EngineStatusResponse(
    name: String,
    isRunning: Boolean,
    info: Option[EngineInfoDetails]
  )

  final case class EngineInfoDetails(
    name: String,
    author: String
  )

  final case class EvaluationResponse(
    engineName: String,
    fen: String,
    depth: Int,
    score: String,
    bestMove: String
  )

  final case class BestMoveResponse(
    engineName: String,
    fen: String,
    depth: Int,
    bestMove: String
  )

  final case class EvaluationRequest(
    fen: String,
    depth: Option[Int] = None
  )

  final case class BestMoveRequest(
    fen: String,
    depth: Option[Int] = None
  )

  final case class EnginesListResponse(engines: List[EngineInfo], total: Int)

  final case class MoveSuggestionRequest(
    fen: String,
    engineName: String = "stockfish",
    depth: Option[Int] = None
  )

  final case class MoveSuggestionResponse(
    bestMove: String,
    score: String,
    depth: Int,
    engineName: String
  )

  given ReadWriter[TimeControlInfo]  = macroRW

  given optionTimeControlInfoRW: ReadWriter[Option[TimeControlInfo]] = readwriter[Value].bimap(
    opt => opt match {
      case Some(t) => upickle.default.writeJs(t)
      case None    => ujson.Null
    },
    json => json match {
      case ujson.Null => None
      case obj        => Some(upickle.default.read[TimeControlInfo](obj))
    }
  )

  given optionStringRW: ReadWriter[Option[String]] = readwriter[Value].bimap(
    opt => opt match {
      case Some(s) => ujson.Str(s)
      case None    => ujson.Null
    },
    json => json match {
      case ujson.Null    => None
      case ujson.Str(s)  => Some(s)
      case ujson.Arr(_)  => None
      case other         => Some(other.str)
    }
  )

  given optionListStringRW: ReadWriter[Option[List[String]]] = readwriter[Value].bimap(
    opt => opt match {
      case Some(lst) => ujson.Arr(lst.map(ujson.Str(_))*)
      case None      => ujson.Null
    },
    json => json match {
      case ujson.Null    => None
      case ujson.Arr(vs) => Some(vs.map(_.str).toList)
      case _             => None
    }
  )

  given ReadWriter[MoveRequest]      = macroRW
  given ReadWriter[CreatedGameResponse] = macroRW
  given ReadWriter[GameResultInfo]   = macroRW
  given ReadWriter[GameStateResponse]   = macroRW
  given ReadWriter[GameSummary]         = macroRW
  given ReadWriter[GamesListResponse]   = macroRW
  given ReadWriter[ActionResponse]      = macroRW
  given ReadWriter[ErrorResponse]       = macroRW
  given ReadWriter[GameInfos]           = macroRW
  given ReadWriter[GameStatusResponse]  = macroRW
  given ReadWriter[CreateGameRequest]  = macroRW
  given ReadWriter[ExportRequest]       = macroRW
  given ReadWriter[ExportResponse]      = macroRW
  given ReadWriter[ApiMetadata]         = macroRW
  given ReadWriter[ResignRequest]       = macroRW
  given ReadWriter[BotMoveRequest]      = macroRW
  given ReadWriter[BotInfoResponse]     = macroRW
  given ReadWriter[AvailableBotsResponse] = macroRW
  given ReadWriter[LeaderboardEntry]      = macroRW
  given ReadWriter[LeaderboardResponse]   = macroRW
  given ReadWriter[HealthStatus]          = macroRW
  given ReadWriter[MetricsResponse]       = macroRW
  given ReadWriter[AnalyticsExportRequest] = macroRW
  given ReadWriter[AnalyticsExportResponse] = macroRW
  given ReadWriter[GameAnalytics]         = macroRW
  given ReadWriter[OpeningInfo]           = macroRW
  given ReadWriter[OpeningMatchInfo]      = macroRW
  given ReadWriter[OpeningsListResponse]  = macroRW
  given ReadWriter[OpeningLookupRequest]  = macroRW
  given ReadWriter[OpeningSearchRequest]  = macroRW
  given ReadWriter[OpeningCategoryResponse] = macroRW
  given ReadWriter[EngineInfo] = macroRW
  given ReadWriter[EngineConfigResponse] = macroRW
  given ReadWriter[EngineConfigRequest] = macroRW
  given ReadWriter[EngineStatusResponse] = macroRW
  given ReadWriter[EngineInfoDetails] = macroRW
  given ReadWriter[EvaluationResponse] = macroRW
  given ReadWriter[BestMoveResponse] = macroRW
  given ReadWriter[EvaluationRequest] = macroRW
  given ReadWriter[BestMoveRequest] = macroRW
  given ReadWriter[EnginesListResponse] = macroRW
  given ReadWriter[MoveSuggestionRequest] = macroRW
  given ReadWriter[MoveSuggestionResponse] = macroRW
}
