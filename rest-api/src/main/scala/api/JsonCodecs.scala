package api

import api.JsonCodecs.GameResultInfo
import model.{PositionState, TimeControl, PlayerTime, GameResult}
import service.GameService
import formatter.UciFormatter
import upickle.default.{ReadWriter, macroRW}

object JsonCodecs {

  final case class MoveRequest(from: String, to: String, promotion: Option[String] = None, castling: Option[String] = None) {
    def toUci: String = {
      val fromTrimmed = from.trim.toLowerCase
      val toTrimmed = to.trim.toLowerCase

      // Handle castling
      castling match {
        case Some("kingside") | Some("0-0") | Some("short") => "00"
        case Some("queenside") | Some("0-0-0") | Some("long") => "000"
        case _ =>
          val promoTrimmed = promotion.map(_.trim.toLowerCase).filter(_.nonEmpty)
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
    remainingTimeMs: Option[Long]
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
    legalMoves: Option[List[String]]
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

      val whiteTimeInfo = state.whiteTime.map { pt =>
        TimeControlInfo(pt.remainingTimeMs, 0, Some(pt.getCurrentTime))
      }

      val blackTimeInfo = state.blackTime.map { pt =>
        TimeControlInfo(pt.remainingTimeMs, 0, Some(pt.getCurrentTime))
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
        legalMoves = legalMovesStr
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
  
  final case class CreateGameRequest(whitePlayer: String, blackPlayer: String, timeControl: Option[String] = None)

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

  final case class AvailableBotsResponse(bots: List[String])

  given ReadWriter[MoveRequest]      = macroRW
  given ReadWriter[CreatedGameResponse] = macroRW
  given ReadWriter[TimeControlInfo]  = macroRW
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
  given ReadWriter[AvailableBotsResponse] = macroRW
}
