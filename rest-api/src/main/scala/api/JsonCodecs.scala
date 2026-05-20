package api

import model.PositionState
import service.GameService
import formatter.UciFormatter
import upickle.default.{ReadWriter, macroRW}

object JsonCodecs {

  final case class MoveRequest(from: String, to: String, promotion: Option[String] = None) {
    def toUci: String = {
      val fromTrimmed = from.trim.toLowerCase
      val toTrimmed = to.trim.toLowerCase
      val promoTrimmed = promotion.map(_.trim.toLowerCase).filter(_.nonEmpty)
      
      promoTrimmed match {
        case Some(promo) => s"$fromTrimmed$toTrimmed$promo"
        case None        => s"$fromTrimmed$toTrimmed"
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

  final case class GameStateResponse(
    gameId: String,
    fen: String,
    turn: String,
    moveHistory: List[String],
    isGameOver: Boolean,
    winner: Option[String]
  )

  object GameStateResponse {
    /** Create GameStateResponse from PositionState */
    def fromPositionState(gameId: String, state: PositionState): GameStateResponse = {
      GameStateResponse(
        gameId = gameId,
        fen = formatter.FenFormatter.fenFormatter(state),
        turn = state.turn.toString,
        moveHistory = UciFormatter.uciListFormatter(state.moveHistory).split("\\s+").toList,
        isGameOver = GameService.isGameOver(state),
        winner = GameService.winner(state).map(_.toString)
      )
    }
  }

  final case class GameSummary(
    gameId: String,
    turn: String,
    isGameOver: Boolean,
    moveCount: Int
  )

  final case class GamesListResponse(games: List[GameSummary], total: Int)

  final case class ActionResponse(message: String)

  final case class ErrorResponse(error: String)

  final case class GameInfos(name: String, version: String, status: String)
  
  final case class GameStatusResponse(
    gameId: String,
    isGameOver: Boolean,
    winner: Option[String],
    turn: String,
    moveCount: Int
  )
  
  final case class CreateGameRequest(whitePlayer: String, blackPlayer: String)

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

  given ReadWriter[MoveRequest]      = macroRW
  given ReadWriter[CreatedGameResponse] = macroRW
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
}
