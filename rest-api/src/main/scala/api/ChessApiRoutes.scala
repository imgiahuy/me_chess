package api

import scala.language.postfixOps
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes, headers}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{RejectionHandler, Route}
import api.JsonCodecs.{ActionResponse, CreateGameRequest, CreatedGameResponse, ErrorResponse, GameInfos, GameStateResponse, GameSummary, GamesListResponse, MoveRequest, ResignRequest, BotMoveRequest, AvailableBotsResponse}



/** REST API routes for chess game management and play. */
class ChessApiRoutes(sessionController: GameSessionController)(implicit system: ActorSystem[?]) {

  private def jsonResponse[T: upickle.default.Writer](obj: T): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, upickle.default.write(obj))

  private def parseJson[T: upickle.default.Reader](body: String): Either[String, T] =
    try {
      Right(upickle.default.read[T](body))
    } catch {
      case e: Exception => Left(s"Invalid JSON request body: ${e.getMessage}")
    }

  private val apiVersion = "v1"

  private def corsHeaders: List[HttpHeader] = List(
    headers.`Access-Control-Allow-Origin`.*,
    headers.`Access-Control-Allow-Methods`(akka.http.scaladsl.model.HttpMethods.GET, akka.http.scaladsl.model.HttpMethods.POST, akka.http.scaladsl.model.HttpMethods.DELETE, akka.http.scaladsl.model.HttpMethods.OPTIONS),
    headers.`Access-Control-Allow-Headers`("Content-Type", "Authorization"),
    headers.`Access-Control-Max-Age`(3600)
  )

  private def withCorsHeaders: Route => Route = { route =>
    respondWithHeaders(corsHeaders) {
      route
    }
  }

  private def validateGameId(gameId: String): Either[String, Unit] = {
    if (gameId.trim.isEmpty) Left("Game ID cannot be empty")
    else if (gameId.length > 100) Left("Game ID is too long (max 100 characters)")
    else Right(())
  }

  val routes: Route =
    withCorsHeaders {
      pathPrefix(apiVersion / "chess") {
        (
          // ─── Game Lifecycle ───────────────────────────────────────────────────────

          /** POST /v1/chess/games - Create a new game */
          post {
            path("games") {
              entity(as[String]) { body =>
                parseJson[CreateGameRequest](body) match {
                  case Right(request) =>
                    try {
                      val gameId = sessionController.createGame(request.whitePlayer, request.blackPlayer)
                      complete(StatusCodes.Created, jsonResponse(CreatedGameResponse(gameId, "Game created successfully")))
                    } catch {
                      case e: Exception =>
                        complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to create game: ${e.getMessage}")))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
        ) ~(
          // ─── Get Game State ───────────────────────────────────────────────────

          /** GET /v1/chess/games/{gameId} - Get current game state */
          get {
            path("games" / Segment) { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.getGame(gameId) match {
                    case Some(state) =>
                      val response = GameStateResponse.fromPositionState(gameId, state)
                      complete(jsonResponse(response))
                    case None =>
                      complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── Apply Moves ──────────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/moves - Apply a move */
          post {
            path("games" / Segment / "moves") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  entity(as[String]) { body =>
                    if (body.trim.isEmpty) {
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse("Request body cannot be empty")))
                    } else {
                      parseJson[MoveRequest](body) match {
                        case Right(moveReq) =>
                          moveReq.validate match {
                            case Right(_) =>
                              sessionController.getGame(gameId) match {
                                case Some(_) =>
                                  val input = moveReq.toUci
                                  sessionController.makeMove(gameId, input) match {
                                    case Right(newState) =>
                                      val response = GameStateResponse.fromPositionState(gameId, newState)
                                      complete(jsonResponse(response))
                                    case Left(reason) =>
                                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(reason)))
                                  }
                                case None =>
                                  complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                              }
                            case Left(validationError) =>
                              complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(validationError)))
                          }
                        case Left(error) =>
                          complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                      }
                    }
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── List Games ───────────────────────────────────────────────────────

          /** GET /v1/chess/games - List all active games */
          get {
            path("games") {
              try {
                val summaries = sessionController.getGameSummaries()
                val gameSummaries = summaries.map { case (gameId, turn, moveCount, isGameOver) =>
                  GameSummary(
                    gameId = gameId,
                    turn = turn,
                    isGameOver = isGameOver,
                    moveCount = moveCount
                  )
                }
                complete(jsonResponse(GamesListResponse(gameSummaries, gameSummaries.length)))
              } catch {
                case e: Exception =>
                  complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to list games: ${e.getMessage}")))
              }
            }
          }
        ) ~(
          // ─── Delete Game ──────────────────────────────────────────────────────

          /** DELETE /v1/chess/games/{gameId} - Delete a game session */
          delete {
            path("games" / Segment) { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  if (sessionController.deleteGame(gameId)) {
                    complete(jsonResponse(ActionResponse(s"Game $gameId deleted successfully")))
                  } else {
                    complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── Save Game ────────────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/save - Save a game to disk */
          post {
            path("games" / Segment / "save") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.saveGame(gameId) match {
                    case Right(_) =>
                      complete(jsonResponse(ActionResponse(s"Game $gameId saved successfully")))
                    case Left(error) =>
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── Load Game ────────────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/load - Load a saved game from database */
          post {
            path("games" / Segment / "load") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.loadGame(gameId) match {
                    case Right(newGameId) =>
                      complete(StatusCodes.Created, jsonResponse(CreatedGameResponse(newGameId, "Game loaded successfully")))
                    case Left(error) =>
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── Load Latest Game ─────────────────────────────────────────────────

          /** POST /v1/chess/games/load-latest - Load the latest saved game from database */
          post {
            path("games" / "load-latest") {
              sessionController.loadLatestGame() match {
                case Right(newGameId) =>
                  complete(StatusCodes.Created, jsonResponse(CreatedGameResponse(newGameId, "Latest game loaded successfully")))
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── Game Status ──────────────────────────────────────────────────────

          /** GET /v1/chess/games/{gameId}/status - Get game status */
          get {
            path("games" / Segment / "status") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.getGame(gameId) match {
                    case Some(state) =>
                      val response = JsonCodecs.GameStatusResponse(
                        gameId = gameId,
                        isGameOver = service.GameService.isGameOver(state),
                        winner = service.GameService.winner(state).map(_.toString),
                        turn = state.turn.toString,
                        moveCount = state.moveHistory.length
                      )
                      complete(jsonResponse(response))
                    case None =>
                      complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
        ) ~(
          // ─── PGN Export ───────────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/export - Export game to PGN */
          post {
            path("games" / Segment / "export") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  entity(as[String]) { body =>
                    parseJson[JsonCodecs.ExportRequest](body) match {
                      case Right(request) =>
                        sessionController.getGame(gameId) match {
                          case Some(state) =>
                            try {
                              val pgnContent = sessionController.exportToPgn(gameId, request.event, request.site)
                              val response = JsonCodecs.ExportResponse(
                                gameId = gameId,
                                pgnContent = pgnContent,
                                filename = s"game_${gameId}.pgn"
                              )
                              complete(jsonResponse(response))
                            } catch {
                              case e: Exception =>
                                complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to export PGN: ${e.getMessage}")))
                            }
                          case None =>
                            complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                        }
                      case Left(error) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                    }
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
         ) ~(
           // ─── Resign ───────────────────────────────────────────────────────────

           /** POST /v1/chess/games/{gameId}/resign - Player resigns */
           post {
             path("games" / Segment / "resign") { gameId =>
               validateGameId(gameId) match {
                 case Right(_) =>
                   entity(as[String]) { body =>
                     parseJson[ResignRequest](body) match {
                       case Right(request) =>
                         sessionController.getGame(gameId) match {
                           case Some(state) =>
                             try {
                               val color = if (request.color.toLowerCase == "white") model.White else model.Black
                               val newState = sessionController.resign(gameId, color)
                               val response = GameStateResponse.fromPositionState(gameId, newState)
                               complete(jsonResponse(response))
                             } catch {
                               case e: Exception =>
                                 complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to process resignation: ${e.getMessage}")))
                             }
                           case None =>
                             complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                         }
                       case Left(error) =>
                         complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                     }
                   }
                 case Left(error) =>
                   complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
               }
             }
           }
         ) ~(
           // ─── Bot Moves ────────────────────────────────────────────────────────

           /** POST /v1/chess/games/{gameId}/bot-move - Bot plays a move */
           post {
             path("games" / Segment / "bot-move") { gameId =>
               validateGameId(gameId) match {
                 case Right(_) =>
                   entity(as[String]) { body =>
                     parseJson[BotMoveRequest](body) match {
                       case Right(request) =>
                         sessionController.getGame(gameId) match {
                           case Some(state) =>
                             try {
                               val result = sessionController.playBotMove(gameId, request.botType)
                               result match {
                                 case Right(newState) =>
                                   val response = GameStateResponse.fromPositionState(gameId, newState)
                                   complete(jsonResponse(response))
                                 case Left(error) =>
                                   complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                               }
                             } catch {
                               case e: Exception =>
                                 complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Bot error: ${e.getMessage}")))
                             }
                           case None =>
                             complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                         }
                       case Left(error) =>
                         complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                     }
                   }
                 case Left(error) =>
                   complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
               }
             }
           }
         ) ~(
           // ─── Available Bots ───────────────────────────────────────────────────

           /** GET /v1/chess/bots - Get available bot types */
           get {
             path("bots") {
               try {
                 val bots = sessionController.getAvailableBots()
                 complete(jsonResponse(AvailableBotsResponse(bots)))
               } catch {
                 case e: Exception =>
                   complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to get available bots: ${e.getMessage}")))
               }
             }
           }
         ) ~(
           // ─── Health / Info ────────────────────────────────────────────────────

           /** GET /v1/chess/info - API information */
           get {
             path("info") {
               complete(jsonResponse(GameInfos(
                 "ME Chess REST API",
                 "1.0.0",
                 "running"
               )))
             }
           }
         )
      }
    }
}

