package rest

import domain.engine.GameState
import domain.model._
import play.api.libs.json._
// Import Color from domain.model, not from scalafx to avoid ambiguity
import domain.model.{Black, White}

/** JSON serializers for chess domain models. */
object JsonSerializers {

  // ─── Position JSON ────────────────────────────────────────────────────────

  implicit val positionFormat: Format[Position] = new Format[Position] {
    def reads(json: JsValue): JsResult[Position] = json match {
      case JsString(s) =>
        Position.fromAlgebraic(s)
          .map(JsSuccess(_))
          .getOrElse(JsError(s"Invalid position: $s"))
      case _ => JsError("Expected string for Position")
    }

    def writes(pos: Position): JsValue = JsString(pos.toAlgebraic)
  }

  // ─── Color JSON ───────────────────────────────────────────────────────────

  implicit val colorFormat: Format[Color] = new Format[Color] {
    def reads(json: JsValue): JsResult[Color] = json match {
      case JsString("white") => JsSuccess(White)
      case JsString("black") => JsSuccess(Black)
      case _ => JsError("Expected 'white' or 'black'")
    }

    def writes(color: Color): JsValue = JsString(color.toString.toLowerCase)
  }

  // ─── PieceType JSON ───────────────────────────────────────────────────────

  implicit val pieceTypeFormat: Format[PieceType] = new Format[PieceType] {
    def reads(json: JsValue): JsResult[PieceType] = json match {
      case JsString("king") => JsSuccess(King)
      case JsString("queen") => JsSuccess(Queen)
      case JsString("rook") => JsSuccess(Rook)
      case JsString("bishop") => JsSuccess(Bishop)
      case JsString("knight") => JsSuccess(Knight)
      case JsString("pawn") => JsSuccess(Pawn)
      case _ => JsError("Invalid piece type")
    }

    def writes(pt: PieceType): JsValue =
      JsString(pt.toString.toLowerCase)
  }

  // ─── Piece JSON ───────────────────────────────────────────────────────────

  implicit val pieceFormat: Format[Piece] = Json.format[Piece]

  // ─── Move JSON ────────────────────────────────────────────────────────────

  implicit val moveFormat: Format[Move] = Json.format[Move]

  // ─── Board JSON ───────────────────────────────────────────────────────────

  implicit val boardFormat: Format[Board] = new Format[Board] {
    def reads(json: JsValue): JsResult[Board] = {
      (json \ "squares").validate[Map[String, Piece]].map { squaresMap =>
        val squares = squaresMap.flatMap { case (posStr, piece) =>
          Position.fromAlgebraic(posStr).map(_ -> piece)
        }
        Board(squares)
      }
    }

    def writes(board: Board): JsValue = {
      val squares = board.squares.map { case (pos, piece) =>
        pos.toAlgebraic -> piece
      }
      Json.obj("squares" -> squares)
    }
  }

  // ─── GameState JSON ───────────────────────────────────────────────────────

  implicit val gameStateFormat: Format[GameState] = Json.format[GameState]

  // ─── API Request/Response models ──────────────────────────────────────────

  case class MoveRequest(from: String, to: String)
  implicit val moveRequestFormat: Format[MoveRequest] = Json.format[MoveRequest]

  case class GameResponse(
    board: Board,
    currentTurn: Color,
    moveHistory: List[Move],
    isGameOver: Boolean,
    winner: Option[Color]
  )
  implicit val gameResponseFormat: Format[GameResponse] = Json.format[GameResponse]

  case class ErrorResponse(error: String)
  implicit val errorResponseFormat: Format[ErrorResponse] = Json.format[ErrorResponse]

  case class InfoResponse(message: String, data: Option[JsValue] = None)
  implicit val infoResponseFormat: Format[InfoResponse] = Json.format[InfoResponse]
}

