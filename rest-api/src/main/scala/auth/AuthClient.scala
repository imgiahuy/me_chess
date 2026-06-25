package auth

import scala.util.{Try, Success, Failure}
import org.apache.http.client.methods.{HttpPost}
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import upickle.default._

/** Token validation response */
case class TokenValidationResponse(
    valid: Boolean,
    user_id: Option[String] = None,
    username: Option[String] = None,
    error: Option[String] = None
)

/** Auth client for token validation */
class AuthClient(authServiceUrl: String) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val httpClient: CloseableHttpClient = HttpClients.createDefault()
  
  private val validateEndpoint = s"$authServiceUrl/v1/auth/validate"
  
  /** Validate access token */
  def validateToken(accessToken: String): Either[String, TokenValidationResponse] = {
    Try {
      val httpPost = new HttpPost(validateEndpoint)
      httpPost.setHeader("Content-Type", "application/json")
      
      val tokenJson = ujson.Obj(
        "access_token" -> accessToken
      )
      
      httpPost.setEntity(new StringEntity(tokenJson.toString()))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 200) {
        val validationResponse = read[TokenValidationResponse](responseBody)
        Right(validationResponse)
      } else {
        Left(s"Token validation failed: $responseBody")
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

// JSON codecs
given ReadWriter[TokenValidationResponse] = macroRW
