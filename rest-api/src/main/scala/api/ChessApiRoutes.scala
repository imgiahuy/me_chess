package api

import api.JsonSerializers.{ErrorResponse, GameResponse, MoveRequest}

import scala.language.postfixOps
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import controller.GameService
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import domain.model.Move
import play.api.libs.json._
import api.JsonSerializers._

/** REST API routes for chess game management and play. */
class ChessApiRoutes(gameRepository: GameRepository)(implicit system: ActorSystem[?])
    extends PlayJsonSupport {

  private val apiVersion = "v1"

  val routes: Route =
    pathPrefix(apiVersion / "chess") {
      (
        // ─── Game Lifecycle ───────────────────────────────────────────────────────

        /** POST /v1/chess/games - Create a new game */
        post {
          path("games") {
            val gameId = gameRepository.createGame()
            complete(Json.obj("gameId" -> gameId, "message" -> "Game created successfully"))
          }
        }
      ) ~(
        // ─── Get Game State ───────────────────────────────────────────────────

        /** GET /v1/chess/games/{gameId} - Get current game state */
        get {
          path("games" / Segment) { gameId =>
            gameRepository.getGame(gameId) match {
              case Some(state) =>
                val isGameOver = GameService.isGameOver(state)
                val winner = GameService.winner(state)
                val response = GameResponse(
                  board = state.board,
                  currentTurn = state.currentTurn,
                  moveHistory = state.moveHistory.reverse,
                  isGameOver = isGameOver,
                  winner = winner
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
              gameRepository.getGame(gameId) match {
                case Some(currentState) =>
                  // Parse move from algebraic notation
                  val moveOpt = Move.fromAlgebraic(moveReq.from + moveReq.to)
                  moveOpt match {
                    case Some(move) =>
                      // Validate and apply move
                      GameService.applyMove(currentState, move) match {
                        case Right(newState) =>
                          gameRepository.updateGame(gameId, newState)
                          val isGameOver = GameService.isGameOver(newState)
                          val winner = GameService.winner(newState)
                          val response = GameResponse(
                            board = newState.board,
                            currentTurn = newState.currentTurn,
                            moveHistory = newState.moveHistory.reverse,
                            isGameOver = isGameOver,
                            winner = winner
                          )
                          complete(response)
                        case Left(reason) =>
                          complete(StatusCodes.BadRequest, ErrorResponse(reason))
                      }
                    case None =>
                      complete(StatusCodes.BadRequest, ErrorResponse(s"Invalid move format: ${moveReq.from}${moveReq.to}"))
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
            val gameIds = gameRepository.listGames()
            complete(Json.obj("games" -> gameIds, "count" -> gameIds.length))
          }
        }
      ) ~(
        // ─── Delete Game ──────────────────────────────────────────────────────

        /** DELETE /v1/chess/games/{gameId} - Delete/end a game */
        delete {
          path("games" / Segment) { gameId =>
            if (gameRepository.deleteGame(gameId)) {
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

