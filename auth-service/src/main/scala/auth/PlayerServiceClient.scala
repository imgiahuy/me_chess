package auth

import scala.util.{Try, Success, Failure}
import org.apache.http.client.methods.{HttpPost, HttpGet}
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import upickle.default._

/** Player service client for creating players */
class PlayerServiceClient(playerServiceUrl: String) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val httpClient: CloseableHttpClient = HttpClients.createDefault()
  
  private val createPlayerEndpoint = s"$playerServiceUrl/v1/players"
  
  /** Create player in player-service */
  def createPlayer(username: String, email: String): Either[String, String] = {
    Try {
      val httpPost = new HttpPost(createPlayerEndpoint)
      httpPost.setHeader("Content-Type", "application/json")
      
      val playerJson = ujson.Obj(
        "username" -> username,
        "email" -> email
      )
      
      httpPost.setEntity(new StringEntity(playerJson.toString()))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 201) {
        val responseObj = ujson.read(responseBody)
        val playerId = responseObj("id").str
        Right(playerId)
      } else {
        Left(s"Player creation failed: $responseBody")
      }
    }.toEither match {
      case Right(result) => result
      case Left(e) => Left(e.getMessage)
    }
  }
  
  /** Close HTTP client */
  def close(): Unit = {
    Try(httpClient.close())
  }
}
