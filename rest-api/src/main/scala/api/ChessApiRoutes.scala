package api

import scala.language.postfixOps
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes, headers, sse}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{RejectionHandler, Route, Directive0}
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.stream.scaladsl.Source
import scala.concurrent.duration._
import api.JsonCodecs.{ActionResponse, CreateGameRequest, CreatedGameResponse, ErrorResponse, GameInfos, GameStateResponse, GameSummary, GamesListResponse, MoveRequest, ResignRequest, BotMoveRequest, BotInfoResponse, AvailableBotsResponse, LeaderboardEntry, LeaderboardResponse, HealthStatus, MetricsResponse, AnalyticsExportRequest, AnalyticsExportResponse, GameAnalytics, OpeningInfo, OpeningMatchInfo, OpeningsListResponse, OpeningLookupRequest, OpeningSearchRequest, OpeningCategoryResponse, EngineInfo, EngineConfigResponse, EngineConfigRequest, EngineStatusResponse, EvaluationRequest, BestMoveRequest, EnginesListResponse, MoveSuggestionRequest, MoveSuggestionResponse}
import openings.OpeningCatalog
import java.io.File
import java.time.Instant
import java.lang.management.ManagementFactory


/** REST API routes for chess game management and play. */
class ChessApiRoutes(sessionController: GameSessionController, engineController: EngineController)(implicit system: ActorSystem[?]) extends EventStreamMarshalling {

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

  /** Optional authentication directive - validates token if auth service is available */
  private def optionalAuth: Directive0 = {
    pass // For now, authentication is optional - we'll enforce it later
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
                println(s"[DEBUG] Received request body: $body")
                parseJson[CreateGameRequest](body) match {
                  case Right(request) =>
                    try {
                      // Parse time control if provided
                      println(s"[DEBUG] Received timeControl: ${request.timeControl}")
                      val timeControl = Option(request.timeControl) match {
                        case Some(tcStr) =>
                          tcStr.toLowerCase match {
                            case "bullet" => model.TimeControl.BULLET
                            case "blitz" => model.TimeControl.BLITZ
                            case "rapid" => model.TimeControl.RAPID
                            case "classical" => model.TimeControl.CLASSICAL
                            case "unlimited" => model.TimeControl.UNLIMITED
                            case _ => model.TimeControl.UNLIMITED
                          }
                        case None => model.TimeControl.UNLIMITED
                      }
                      println(s"[DEBUG] Creating game with timeControl: $timeControl")
                      val gameId = sessionController.createGameWithTimeControl(request.whitePlayer, request.blackPlayer, timeControl)
                      println(s"[DEBUG] Game created with ID: $gameId")
                      complete(StatusCodes.Created, jsonResponse(CreatedGameResponse(gameId, "Game created successfully")))
                    } catch {
                      case e: Exception =>
                        println(s"[ERROR] Failed to create game: ${e.getMessage}")
                        e.printStackTrace()
                        complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to create game: ${e.getMessage}")))
                    }
                  case Left(error) =>
                    println(s"[ERROR] JSON parsing failed: $error")
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          // ─── Get Game State ───────────────────────────────────────────────────

          /** GET /v1/chess/games/{gameId} - Get current game state (checks for timeout) */
          get {
            path("games" / Segment) { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  // First check for timeout and update game state if needed
                  sessionController.checkAndApplyTimeout(gameId) match {
                    case Right(state) =>
                      println(s"[DEBUG] State whiteTime: ${state.whiteTime}, blackTime: ${state.blackTime}, timeControl: ${state.timeControl}")
                      val response = GameStateResponse.fromPositionState(gameId, state, includeLegalMoves = true)
                      println(s"[DEBUG] Game state response: $response")
                      complete(jsonResponse(response))
                    case Left(_) =>
                      complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          // ─── Apply Moves ──────────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/moves - Apply a move */
          post {
            path("games" / Segment / "moves") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  entity(as[String]) { body =>
                    println(s"[DEBUG] Move request body: $body")
                    if (body.trim.isEmpty) {
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse("Request body cannot be empty")))
                    } else {
                      parseJson[MoveRequest](body) match {
                        case Right(moveReq) =>
                          println(s"[DEBUG] Parsed move request: $moveReq")
                          moveReq.validate match {
                            case Right(_) =>
                              sessionController.getGame(gameId) match {
                                case Some(_) =>
                                  val input = moveReq.toUci
                                  println(s"[DEBUG] Move UCI: $input")
                                  sessionController.makeMove(gameId, input) match {
                                    case Right(_) =>
                                      // Check for timeout after move and return updated state
                                      sessionController.checkAndApplyTimeout(gameId) match {
                                        case Right(newState) =>
                                          val response = GameStateResponse.fromPositionState(gameId, newState)
                                          complete(jsonResponse(response))
                                        case Left(error) =>
                                          complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                                      }
                                    case Left(reason) =>
                                      println(s"[ERROR] Move failed: $reason")
                                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(reason)))
                                  }
                                case None =>
                                  complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                              }
                            case Left(validationError) =>
                              println(s"[ERROR] Validation failed: $validationError")
                              complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(validationError)))
                          }
                        case Left(error) =>
                          println(s"[ERROR] JSON parsing failed: $error")
                          complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                      }
                    }
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          // ─── List Games ───────────────────────────────────────────────────────

          /** GET /v1/chess/games - List all active games */
          get {
            path("games") {
              try {
                val summaries = sessionController.getGameSummaries()
                val gameSummaries = summaries.map { case (gameId, turn, moveCount, isGameOver) =>
                  val gameResultStr = if (isGameOver) "finished" else "ongoing"
                  GameSummary(
                    gameId = gameId,
                    turn = turn,
                    gameResult = gameResultStr,
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
          ) ~ (
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
          ) ~ (
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
          ) ~ (
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
          ) ~ (
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
          ) ~ (
          // ─── Game Status ──────────────────────────────────────────────────────

          /** GET /v1/chess/games/{gameId}/status - Get game status (checks for timeout) */
          get {
            path("games" / Segment / "status") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  // First check for timeout and update game state if needed
                  sessionController.checkAndApplyTimeout(gameId) match {
                    case Right(stateAfterTimeout) =>
                      val gameResultInfo = stateAfterTimeout.gameResult match {
                        case model.Ongoing => JsonCodecs.GameResultInfo("ongoing", None, None)
                        case model.Checkmate(winner) => JsonCodecs.GameResultInfo("checkmate", None, Some(winner.toString))
                        case model.Draw(reason) => JsonCodecs.GameResultInfo("draw", Some(reason.toString), None)
                        case model.Resignation(winner) => JsonCodecs.GameResultInfo("resignation", None, Some(winner.toString))
                        case model.TimeOut(winner) => JsonCodecs.GameResultInfo("timeout", None, Some(winner.toString))
                      }

                      val isUnlimitedStatus = stateAfterTimeout.timeControl.isEmpty || stateAfterTimeout.timeControl.exists(_.initialTimeMs == Long.MaxValue)

                      val whiteTimeInfo = if (isUnlimitedStatus) None else stateAfterTimeout.whiteTime.map { pt =>
                        val initial = stateAfterTimeout.timeControl.map(_.initialTimeMs).getOrElse(0L)
                        val increment = stateAfterTimeout.timeControl.map(_.incrementMs).getOrElse(0L)
                        val delay = stateAfterTimeout.timeControl.map(_.delayMs).getOrElse(0L)
                        val isWhiteActive = stateAfterTimeout.turn == model.White
                        val remaining = if (stateAfterTimeout.isPaused || !isWhiteActive) pt.remainingTimeMs else pt.getCurrentTime
                        JsonCodecs.TimeControlInfo(initial, increment, remaining, delay)
                      }

                      val blackTimeInfo = if (isUnlimitedStatus) None else stateAfterTimeout.blackTime.map { pt =>
                        val initial = stateAfterTimeout.timeControl.map(_.initialTimeMs).getOrElse(0L)
                        val increment = stateAfterTimeout.timeControl.map(_.incrementMs).getOrElse(0L)
                        val delay = stateAfterTimeout.timeControl.map(_.delayMs).getOrElse(0L)
                        val isBlackActive = stateAfterTimeout.turn == model.Black
                        val remaining = if (stateAfterTimeout.isPaused || !isBlackActive) pt.remainingTimeMs else pt.getCurrentTime
                        JsonCodecs.TimeControlInfo(initial, increment, remaining, delay)
                      }

                      val response = JsonCodecs.GameStatusResponse(
                        gameId = gameId,
                        gameResult = gameResultInfo,
                        turn = stateAfterTimeout.turn.toString,
                        moveCount = stateAfterTimeout.moveHistory.length,
                        whiteTime = whiteTimeInfo,
                        blackTime = blackTimeInfo
                      )
                      complete(jsonResponse[JsonCodecs.GameStatusResponse](response))
                    case Left(_) =>
                      complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Game not found: $gameId")))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
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
          ) ~ (
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
                              val colorLower = request.color.toLowerCase
                              val colorOpt = colorLower match {
                                case "white" => Some(model.White)
                                case "black" => Some(model.Black)
                                case _ => None
                              }
                              colorOpt match {
                                case Some(color) =>
                                  val newState = sessionController.resign(gameId, color)
                                  val response = GameStateResponse.fromPositionState(gameId, newState)
                                  complete(jsonResponse(response))
                                case None =>
                                  complete(StatusCodes.BadRequest, jsonResponse(
                                    ErrorResponse(s"Invalid color: ${request.color}. Must be 'white' or 'black'")
                                  ))
                              }
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
          ) ~ (
          // ─── Pause / Resume ───────────────────────────────────────────────────

          /** POST /v1/chess/games/{gameId}/pause - Pause the game */
          post {
            path("games" / Segment / "pause") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.pauseGame(gameId) match {
                    case Right(newState) =>
                      val response = GameStateResponse.fromPositionState(gameId, newState)
                      complete(jsonResponse(response))
                    case Left(error) =>
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/games/{gameId}/resume - Resume a paused game */
          post {
            path("games" / Segment / "resume") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  sessionController.resumeGame(gameId) match {
                    case Right(newState) =>
                      val response = GameStateResponse.fromPositionState(gameId, newState)
                      complete(jsonResponse(response))
                    case Left(error) =>
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                  }
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
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
          ) ~ (
          // ─── Available Bots ───────────────────────────────────────────────────

          /** GET /v1/chess/bots - Get available bot types with metadata */
          get {
            path("bots") {
              try {
                val botInfos = sessionController.getAvailableBotInfos()
                val response = botInfos.map { info =>
                  BotInfoResponse(info.id, info.name, info.difficulty, info.description)
                }
                complete(jsonResponse(AvailableBotsResponse(response)))
              } catch {
                case e: Exception =>
                  complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to get available bots: ${e.getMessage}")))
              }
            }
          }
          ) ~ (
          // ─── Spark Leaderboard ────────────────────────────────────────────────

          /** GET /v1/chess/leaderboard - Player leaderboard from Spark analytics */
          get {
            path("leaderboard") {
              val sparkOutputDir = sys.env.getOrElse("SPARK_OUTPUT_PATH", System.getProperty("java.io.tmpdir") + "/chess-analytics")
              val statsDir = new File(s"$sparkOutputDir/player-stats")
              try {
                val entries = if (statsDir.exists() && statsDir.isDirectory) {
                  val jsonFiles = statsDir.listFiles().filter(f => f.getName.endsWith(".json") && !f.getName.startsWith("_"))
                  val rows = jsonFiles.flatMap { f =>
                    val content = try {
                      val source = scala.io.Source.fromFile(f)
                      try source.mkString finally source.close()
                    } catch {
                      case _: Exception => ""
                    }
                    content.split("\n").filter(_.trim.nonEmpty).flatMap { line =>
                      try {
                        val obj = ujson.read(line)
                        Some((
                          obj("player").str,
                          obj("totalGames").num.toLong,
                          obj("victories").num.toLong,
                          obj("defeats").num.toLong,
                          obj("draws").num.toLong,
                          obj("winRate").num
                        ))
                      } catch {
                        case _: Exception => None
                      }
                    }
                  }
                  rows.sortBy(-_._3).zipWithIndex.map { case ((player, total, wins, losses, draws, wr), idx) =>
                    LeaderboardEntry(idx + 1, player, total, wins, losses, draws, wr)
                  }.toList
                } else {
                  List.empty
                }
                val source = if (entries.isEmpty) "no-spark-data" else "spark-analytics"
                complete(jsonResponse(LeaderboardResponse(entries, Instant.now().toString, source)))
              } catch {
                case e: Exception =>
                  complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to read leaderboard: ${e.getMessage}")))
              }
            }
          }
          ) ~ (
          // ─── Engine Management ────────────────────────────────────────────────

          /** GET /v1/chess/engines - List all engine configurations */
          get {
            path("engines") {
              try {
                val engines = engineController.listEngines()
                complete(jsonResponse(EnginesListResponse(engines, engines.length)))
              } catch {
                case e: Exception =>
                  complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to list engines: ${e.getMessage}")))
              }
            }
          }
          ) ~ (
          /** GET /v1/chess/engines/{name} - Get engine configuration */
          get {
            path("engines" / Segment) { name =>
              engineController.getEngineConfig(name) match {
                case Some(config) =>
                  complete(jsonResponse(config))
                case None =>
                  complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Engine not found: $name")))
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/engines/{name}/start - Start an engine */
          post {
            path("engines" / Segment / "start") { name =>
              engineController.startEngine(name) match {
                case Right(status) =>
                  complete(StatusCodes.OK, jsonResponse(status))
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/engines/{name}/stop - Stop an engine */
          post {
            path("engines" / Segment / "stop") { name =>
              engineController.stopEngine(name) match {
                case Right(status) =>
                  complete(StatusCodes.OK, jsonResponse(status))
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          /** GET /v1/chess/engines/{name}/status - Get engine status */
          get {
            path("engines" / Segment / "status") { name =>
              engineController.getEngineStatus(name) match {
                case Right(status) =>
                  complete(StatusCodes.OK, jsonResponse(status))
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/engines/{name}/evaluate - Evaluate position */
          post {
            path("engines" / Segment / "evaluate") { name =>
              entity(as[String]) { body =>
                parseJson[EvaluationRequest](body) match {
                  case Right(request) =>
                    engineController.evaluatePosition(name, request.fen, request.depth.getOrElse(15)) match {
                      case Right(result) =>
                        complete(StatusCodes.OK, jsonResponse(result))
                      case Left(error) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/engines/{name}/bestmove - Get best move */
          post {
            path("engines" / Segment / "bestmove") { name =>
              entity(as[String]) { body =>
                parseJson[BestMoveRequest](body) match {
                  case Right(request) =>
                    engineController.getBestMove(name, request.fen, request.depth.getOrElse(15)) match {
                      case Right(result) =>
                        complete(StatusCodes.OK, jsonResponse(result))
                      case Left(error) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/engines/{name}/register - Register custom engine */
          post {
            path("engines" / Segment / "register") { name =>
              entity(as[String]) { body =>
                parseJson[EngineConfigRequest](body) match {
                  case Right(request) =>
                    engineController.registerEngine(name, request) match {
                      case Right(config) =>
                        complete(StatusCodes.Created, jsonResponse(config))
                      case Left(error) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          /** OPTIONS /v1/chess/suggest - CORS preflight for suggest endpoint */
          options {
            path("suggest") {
              complete(StatusCodes.OK)
            }
          }
          ) ~ (
          /** POST /v1/chess/suggest - Get move suggestion for a position */
          post {
            path("suggest") {
              entity(as[String]) { body =>
                parseJson[MoveSuggestionRequest](body) match {
                  case Right(request) =>
                    engineController.getMoveSuggestion(request.fen, request.engineName, request.depth) match {
                      case Right(suggestion) =>
                        complete(StatusCodes.OK, jsonResponse(suggestion))
                      case Left(error) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
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
          ) ~ (
          // ─── Game Events (SSE) ────────────────────────────────────────────────

          /** GET /v1/chess/games/{gameId}/events - Real-time game state via SSE */
          get {
            path("games" / Segment / "events") { gameId =>
              validateGameId(gameId) match {
                case Right(_) =>
                  val eventSource = Source.tick(0.seconds, 2.seconds, ())
                    .map { _ =>
                      sessionController.checkAndApplyTimeout(gameId) match {
                        case Right(state) =>
                          val response = GameStateResponse.fromPositionState(gameId, state)
                          val data = upickle.default.write(response)
                          sse.ServerSentEvent(data, "game-state")
                        case Left(_) =>
                          sse.ServerSentEvent(s"""{"error":"game not found","gameId":"$gameId"}""", "error")
                      }
                    }
                    .keepAlive(30.seconds, () => sse.ServerSentEvent("", "heartbeat"))
                  complete(eventSource)
                case Left(error) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
              }
            }
          }
          ) ~ (
          // ─── Health Checks ────────────────────────────────────────────────────

          /** GET /v1/chess/health - Overall health */
          get {
            path("health") {
              val activeGames = sessionController.listGames().length
              val status = HealthStatus("UP", "ME Chess REST API", "1.0.0", activeGames, Instant.now().toString)
              complete(jsonResponse(status))
            }
          }
          ) ~ (
          /** GET /v1/chess/health/ready - Readiness probe */
          get {
            path("health" / "ready") {
              complete(jsonResponse(Map("status" -> "READY", "timestamp" -> Instant.now().toString)))
            }
          }
          ) ~ (
          /** GET /v1/chess/health/live - Liveness probe */
          get {
            path("health" / "live") {
              complete(jsonResponse(Map("status" -> "ALIVE", "timestamp" -> Instant.now().toString)))
            }
          }
          ) ~ (
          // ─── Metrics ──────────────────────────────────────────────────────────

          /** GET /v1/chess/metrics - Prometheus-compatible metrics */
          get {
            path("metrics") {
              val runtime = Runtime.getRuntime
              val bean = ManagementFactory.getRuntimeMXBean
              val activeGames = sessionController.listGames().length
              val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
              val heapMax = runtime.maxMemory() / (1024 * 1024)
              val uptimeSeconds = bean.getUptime / 1000
              val metrics = MetricsResponse(
                activeGames = activeGames,
                heapUsedMb = heapUsed,
                heapMaxMb = heapMax,
                uptimeSeconds = uptimeSeconds,
                timestamp = Instant.now().toString
              )
              complete(jsonResponse(metrics))
            }
          }
          ) ~ (
          // ─── Analytics Export ────────────────────────────────────────────────────

          /** POST /v1/chess/analytics/export - Export game analytics */
          post {
            path("analytics" / "export") {
              entity(as[String]) { body =>
                parseJson[AnalyticsExportRequest](body) match {
                  case Right(request) =>
                    try {
                      val exportId = java.util.UUID.randomUUID().toString
                      val games = sessionController.listGames()
                      
                      val analytics = games.flatMap { gameId =>
                        sessionController.getGame(gameId).map { state =>
                          val result = state.gameResult match {
                            case model.Ongoing => "ongoing"
                            case model.Checkmate(winner) => s"checkmate-${winner.toString}"
                            case model.Draw(reason) => s"draw-${reason.toString}"
                            case model.Resignation(winner) => s"resignation-${winner.toString}"
                            case model.TimeOut(winner) => s"timeout-${winner.toString}"
                          }
                          GameAnalytics(
                            gameId = gameId,
                            whitePlayer = state.whitePlayer.name,
                            blackPlayer = state.blackPlayer.name,
                            result = result,
                            moveCount = state.moveHistory.length,
                            timeControl = state.timeControl.map(_.toString),
                            createdAt = state.creationDate.toString,
                            durationSeconds = None,
                            averageMoveTime = None
                          )
                        }
                      }.toList
                      
                      val format = request.format.toLowerCase
                      val content = format match {
                        case "json" => analyticsToJson(analytics)
                        case "csv" => analyticsToCsv(analytics)
                        case "pgn" => analyticsToPgn(analytics)
                        case _ => analyticsToJson(analytics)
                      }
                      
                      val exportDir = new File(System.getProperty("java.io.tmpdir"), "chess-exports")
                      exportDir.mkdirs()
                      val filename = s"analytics-$exportId.$format"
                      val exportFile = new File(exportDir, filename)
                      java.nio.file.Files.write(exportFile.toPath, content.getBytes("UTF-8"))
                      
                      val expiresAt = Instant.now().plusSeconds(3600).toString
                      val response = AnalyticsExportResponse(
                        exportId = exportId,
                        format = format,
                        recordCount = analytics.length,
                        downloadUrl = s"/v1/chess/analytics/download/$exportId",
                        expiresAt = expiresAt
                      )
                      
                      complete(StatusCodes.OK, jsonResponse(response))
                    } catch {
                      case e: Exception =>
                        complete(StatusCodes.InternalServerError, jsonResponse(ErrorResponse(s"Failed to export analytics: ${e.getMessage}")))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          /** GET /v1/chess/analytics/download/{exportId} - Download exported analytics */
          get {
            path("analytics" / "download" / Segment) { exportId =>
              val exportDir = new File(System.getProperty("java.io.tmpdir"), "chess-exports")
              val files = exportDir.listFiles().filter(_.getName.startsWith(s"analytics-$exportId"))
              files.headOption match {
                case Some(file) =>
                  val content = scala.io.Source.fromFile(file).mkString
                  val contentType = file.getName.split("\\.").last match {
                    case "json" => ContentTypes.`application/json`
                    case "csv" => ContentTypes.`text/csv(UTF-8)`
                    case "pgn" => ContentTypes.`text/plain(UTF-8)`
                    case _ => ContentTypes.`application/octet-stream`
                  }
                  complete(HttpEntity(contentType, content.getBytes("UTF-8")))
                case None =>
                  complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Export not found: $exportId")))
              }
            }
          }
          ) ~ (
          // ─── Opening Catalog ─────────────────────────────────────────────────────

          /** GET /v1/chess/openings - List all openings */
          get {
            path("openings") {
              val catalog = OpeningCatalog()
              val openings = catalog.allOpenings.map { o =>
                OpeningInfo(o.name, o.ecoCode, o.moves, o.description, o.category)
              }
              complete(jsonResponse(OpeningsListResponse(openings, openings.length)))
            }
          }
          ) ~ (
          /** GET /v1/chess/openings/categories/{category} - Get openings by category */
          get {
            path("openings" / "categories" / Segment) { category =>
              val catalog = OpeningCatalog()
              val openings = catalog.openingsByCategory(category).map { o =>
                OpeningInfo(o.name, o.ecoCode, o.moves, o.description, o.category)
              }
              complete(jsonResponse(OpeningCategoryResponse(category, openings)))
            }
          }
          ) ~ (
          /** GET /v1/chess/openings/eco/{ecoCode} - Get opening by ECO code */
          get {
            path("openings" / "eco" / Segment) { ecoCode =>
              val catalog = OpeningCatalog()
              catalog.openingByEco(ecoCode) match {
                case Some(opening) =>
                  val info = OpeningInfo(opening.name, opening.ecoCode, opening.moves, opening.description, opening.category)
                  complete(jsonResponse(info))
                case None =>
                  complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Opening not found: $ecoCode")))
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/openings/lookup - Find opening by move sequence */
          post {
            path("openings" / "lookup") {
              entity(as[String]) { body =>
                parseJson[OpeningLookupRequest](body) match {
                  case Right(request) =>
                    val catalog = OpeningCatalog()
                    catalog.findOpening(request.moves) match {
                      case Some(matchInfo) =>
                        val openingInfo = OpeningInfo(matchInfo.opening.name, matchInfo.opening.ecoCode, matchInfo.opening.moves, matchInfo.opening.description, matchInfo.opening.category)
                        complete(jsonResponse(OpeningMatchInfo(openingInfo, matchInfo.matchedMoves, matchInfo.confidence)))
                      case None =>
                        complete(StatusCodes.NotFound, jsonResponse(ErrorResponse("No matching opening found")))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          ) ~ (
          /** POST /v1/chess/openings/search - Search openings by name/description */
          post {
            path("openings" / "search") {
              entity(as[String]) { body =>
                parseJson[OpeningSearchRequest](body) match {
                  case Right(request) =>
                    val catalog = OpeningCatalog()
                    val openings = catalog.searchOpenings(request.query).map { o =>
                      OpeningInfo(o.name, o.ecoCode, o.moves, o.description, o.category)
                    }
                    complete(jsonResponse(OpeningsListResponse(openings, openings.length)))
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(error)))
                }
              }
            }
          }
          )
      }
    }
  
  private def analyticsToJson(analytics: List[GameAnalytics]): String = {
    import JsonCodecs.given_ReadWriter_GameAnalytics
    upickle.default.write(analytics)
  }
  
  private def analyticsToCsv(analytics: List[GameAnalytics]): String = {
    val header = "gameId,whitePlayer,blackPlayer,result,moveCount,timeControl,createdAt,durationSeconds,averageMoveTime,blunders,mistakes,inaccuracies\n"
    val rows = analytics.map { a =>
      s"${a.gameId},${a.whitePlayer},${a.blackPlayer},${a.result},${a.moveCount},${a.timeControl.getOrElse("")},${a.createdAt},${a.durationSeconds.getOrElse("")},${a.averageMoveTime.getOrElse("")},${a.blunders},${a.mistakes},${a.inaccuracies}"
    }
    header + rows.mkString("\n")
  }
  
  private def analyticsToPgn(analytics: List[GameAnalytics]): String = {
    analytics.map { a =>
      s"""[Event "Chess Game"]
         |[Site "ME Chess"]
         |[Date "${a.createdAt.take(10)}"]
         |[White "${a.whitePlayer}"]
         |[Black "${a.blackPlayer}"]
         |[Result "${a.result}"]
         |
         |*""".stripMargin.replaceAll("\n", "\r\n")
    }.mkString("\n\n")
  }
}
