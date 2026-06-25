package auth

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Success, Failure}
import upickle.default._
import shared.http.HttpHelpers

/** Auth routes for authentication service */
class AuthRoutes(keycloakClient: KeycloakClient, playerServiceClient: PlayerServiceClient)(implicit system: ActorSystem[?]) {
  
  private implicit val ec: ExecutionContextExecutor = system.executionContext
  
  import HttpHelpers.{jsonResponse, parseJson}
  
  val routes: Route =
    pathPrefix("v1" / "auth") {
      // POST /v1/auth/login - Login user
      post {
        path("login") {
          entity(as[String]) { body =>
            parseJson[LoginRequest](body) match {
              case Right(request) =>
                keycloakClient.login(request.username, request.password) match {
                  case Right(authResponse) =>
                    complete(StatusCodes.OK, jsonResponse(authResponse))
                  case Left(error) =>
                    complete(StatusCodes.Unauthorized, jsonResponse(Map("error" -> error)))
                }
              case Left(error) =>
                complete(StatusCodes.BadRequest, jsonResponse(Map("error" -> error)))
            }
          }
        }
      } ~
      // POST /v1/auth/register - Register new user
      post {
        path("register") {
          entity(as[String]) { body =>
            parseJson[RegisterRequest](body) match {
              case Right(request) =>
                // First register in Keycloak
                keycloakClient.register(request) match {
                  case Right(userId) =>
                    // Then create player in player-service
                    playerServiceClient.createPlayer(request.username, request.email) match {
                      case Right(playerId) =>
                        complete(StatusCodes.Created, jsonResponse(Map(
                          "user_id" -> userId,
                          "player_id" -> playerId,
                          "message" -> "User registered successfully"
                        )))
                      case Left(error) =>
                        // Rollback: delete user from Keycloak if player creation fails
                        complete(StatusCodes.InternalServerError, jsonResponse(Map(
                          "error" -> s"Player creation failed: $error"
                        )))
                    }
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, jsonResponse(Map("error" -> error)))
                }
              case Left(error) =>
                complete(StatusCodes.BadRequest, jsonResponse(Map("error" -> error)))
            }
          }
        }
      } ~
      // POST /v1/auth/refresh - Refresh access token
      post {
        path("refresh") {
          entity(as[String]) { body =>
            val refreshToken = try {
              val json = ujson.read(body)
              json.obj.get("refresh_token").map(_.str).getOrElse("")
            } catch {
              case _: Exception => ""
            }
            
            if (refreshToken.nonEmpty) {
              keycloakClient.refreshToken(refreshToken) match {
                case Right(authResponse) =>
                  complete(StatusCodes.OK, jsonResponse(authResponse))
                case Left(error) =>
                  complete(StatusCodes.Unauthorized, jsonResponse(Map("error" -> error)))
              }
            } else {
              complete(StatusCodes.BadRequest, jsonResponse(Map("error" -> "refresh_token is required")))
            }
          }
        }
      } ~
      // GET /v1/auth/userinfo - Get user info from token
      get {
        path("userinfo") {
          optionalHeaderValueByName("Authorization") { authHeader =>
            authHeader match {
              case Some(header) if header.startsWith("Bearer ") =>
                val accessToken = header.substring(7)
                keycloakClient.validateToken(accessToken) match {
                  case Right(userInfo) =>
                    complete(StatusCodes.OK, jsonResponse(userInfo))
                  case Left(error) =>
                    complete(StatusCodes.Unauthorized, jsonResponse(Map("error" -> error)))
                }
              case _ =>
                complete(StatusCodes.Unauthorized, jsonResponse(Map("error" -> "Authorization header missing or invalid")))
            }
          }
        }
      } ~
      // POST /v1/auth/validate - Validate token (for other services)
      post {
        path("validate") {
          entity(as[String]) { body =>
            val accessToken = try {
              val json = ujson.read(body)
              json.obj.get("access_token").map(_.str).getOrElse("")
            } catch {
              case _: Exception => ""
            }
            
            if (accessToken.nonEmpty) {
              keycloakClient.validateToken(accessToken) match {
                case Right(userInfo) =>
                  complete(StatusCodes.OK, jsonResponse(ujson.Obj(
                    "valid" -> true,
                    "user_id" -> userInfo.sub,
                    "username" -> userInfo.preferred_username
                  ).toString()))
                case Left(error) =>
                  complete(StatusCodes.OK, jsonResponse(ujson.Obj(
                    "valid" -> false,
                    "error" -> error
                  ).toString()))
              }
            } else {
              complete(StatusCodes.BadRequest, jsonResponse(Map("error" -> "access_token is required")))
            }
          }
        }
      } ~
      // Health check
      get {
        path("health") {
          complete(StatusCodes.OK, jsonResponse(Map("status" -> "healthy")))
        }
      }
    }
}
