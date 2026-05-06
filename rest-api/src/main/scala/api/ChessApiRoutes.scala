package api

import api.JsonSerializers.{ErrorResponse, MoveRequest}

import scala.language.postfixOps
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import model.Move
import play.api.libs.json._
import api.JsonSerializers._
import parser.FEN

/** REST API routes for chess game management and play. */
class ChessApiRoutes(sessionController: GameSessionController)(implicit system: ActorSystem[?])
    extends PlayJsonSupport {

  private val apiVersion = "v1"

  val routes: Route =
    pathPrefix(apiVersion / "chess") {
      (
        // ─── Game Lifecycle ───────────────────────────────────────────────────────

        /** POST /v1/chess/games - Create a new game */
        post {
          path("games") {
            val gameId = sessionController.createGame()
            complete(Json.obj("gameId" -> gameId, "message" -> "Game created successfully"))
          }
        }
      ) ~(
        // ─── Get Game State ───────────────────────────────────────────────────

        /** GET /v1/chess/games/{gameId} - Get current game state */
        get {
          path("games" / Segment) { gameId =>
            sessionController.getGame(gameId) match {
              case Some(state) =>
                val response = GameResponse(
                  board = FEN.boardToFEN(state.board),
                  currentTurn = state.turn,
                  moveHistory = state.moveHistory.reverse,
                )
                complete(response)
              case None =>
                complete(StatusCodes.NotFound, ErrorResponse(s"Game not found: $gameId"))
            }
          }
        }
      ) ~(
        // ─── Apply Moves ──────────────────────────────────────────────────────

        /** POST /v1/chess/games/{gameId}/moves - Apply a move */
        post {
          path("games" / Segment / "moves") { gameId =>
            entity(as[MoveRequest]) { moveReq =>
              sessionController.getGame(gameId) match {
                case Some(_) =>
                  val input = moveReq.from + moveReq.to
                      // Validate and apply move
                  sessionController.makeMove(gameId, input) match {
                    case Right(newState) =>
                      sessionController.updateGame(gameId, newState)
                      val response = GameResponse(
                        board = FEN.boardToFEN(newState.board),
                        currentTurn = newState.turn,
                        moveHistory = newState.moveHistory.reverse,
                      )
                      complete(response)
                    case Left(reason) =>
                      complete(StatusCodes.BadRequest, ErrorResponse(reason))
                  }
                case None =>
                  complete(StatusCodes.NotFound, ErrorResponse(s"Game not found: $gameId"))
              }
            }
          }
        }
      ) ~(
        // ─── List Games ───────────────────────────────────────────────────────

        /** GET /v1/chess/games - List all active games */
        get {
          path("games") {
            val gameIds = sessionController.listGames()
            complete(Json.obj("games" -> gameIds, "count" -> gameIds.length))
          }
        }
      ) ~(
        // ─── Delete Game ──────────────────────────────────────────────────────

        /** DELETE /v1/chess/games/{gameId} - Delete/end a game */
        delete {
          path("games" / Segment) { gameId =>
            if (sessionController.deleteGame(gameId)) {
              complete(Json.obj("message" -> s"Game $gameId deleted successfully"))
            } else {
              complete(StatusCodes.NotFound, ErrorResponse(s"Game not found: $gameId"))
            }
          }
        }
      ) ~(
        // ─── Health / Info ────────────────────────────────────────────────────

        /** GET /v1/chess/info - API information */
        get {
          path("info") {
            complete(Json.obj(
              "name" -> "ME Chess REST API",
              "version" -> "1.0.0",
              "status" -> "running"
            ))
          }
        }
      )
    }
}

