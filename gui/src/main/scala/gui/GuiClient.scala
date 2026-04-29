package gui

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

case class MoveRequest(from: String, to: String)

case class GameResponse(
                         board: String,
                         currentTurn: String,
                         isGameOver: Boolean
                       )

class ChessClient(baseUrl: String)(implicit system: ActorSystem[?])
  extends PlayJsonSupport {

  implicit val ec: ExecutionContext = system.executionContext

  def createGame(): Future[String] = {
    val req = HttpRequest(HttpMethods.POST, uri = s"$baseUrl/v1/chess/games")

    for {
      res <- Http().singleRequest(req)
      json <- Unmarshal(res.entity).to[JsValue]
    } yield (json \ "gameId").as[String]
  }

  def getGame(gameId: String): Future[GameResponse] = {
    val req = HttpRequest(HttpMethods.GET, uri = s"$baseUrl/v1/chess/games/$gameId")

    for {
      res <- Http().singleRequest(req)
      json <- Unmarshal(res.entity).to[JsValue]
    } yield GameResponse(
      board = (json \ "board").as[String],
      currentTurn = (json \ "currentTurn").as[String],
      isGameOver = (json \ "isGameOver").as[Boolean]
    )
  }

  def makeMove(gameId: String, from: String, to: String): Future[GameResponse] = {
    val json = Json.obj("from" -> from, "to" -> to)

    for {
      entity <- Marshal(json).to[RequestEntity]
      req = HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/v1/chess/games/$gameId/moves",
        entity = entity
      )
      res <- Http().singleRequest(req)
      json <- Unmarshal(res.entity).to[JsValue]
    } yield GameResponse(
      board = (json \ "board").as[String],
      currentTurn = (json \ "currentTurn").as[String],
      isGameOver = (json \ "isGameOver").as[Boolean]
    )
  }
}