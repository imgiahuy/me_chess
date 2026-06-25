package auth

import scala.util.{Try, Success, Failure}
import java.util.Base64
import org.apache.http.client.methods.{HttpPost, HttpGet}
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import upickle.default._

/** Keycloak token response */
case class KeycloakTokenResponse(
    access_token: String,
    refresh_token: String,
    expires_in: Int,
    refresh_expires_in: Int,
    token_type: String
)

/** Keycloak user info response */
case class KeycloakUserInfo(
    sub: String,
    preferred_username: String,
    email: String,
    given_name: String,
    family_name: String,
    email_verified: Boolean
)

/** Login request */
case class LoginRequest(username: String, password: String)

/** Register request */
case class RegisterRequest(username: String, email: String, password: String, firstName: String, lastName: String)

/** Auth response */
case class AuthResponse(
    access_token: String,
    refresh_token: String,
    expires_in: Int,
    user_id: String,
    username: String
)

/** Keycloak client for authentication operations */
class KeycloakClient(
    keycloakUrl: String,
    realm: String,
    clientId: String,
    clientSecret: String,
    adminUsername: String = "admin",
    adminPassword: String = "admin"
) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val httpClient: CloseableHttpClient = HttpClients.createDefault()
  
  private val tokenEndpoint = s"$keycloakUrl/realms/$realm/protocol/openid-connect/token"
  private val masterTokenEndpoint = s"$keycloakUrl/realms/master/protocol/openid-connect/token"
  private val usersEndpoint = s"$keycloakUrl/admin/realms/$realm/users"
  
  /** Login user and return tokens */
  def login(username: String, password: String): Either[String, AuthResponse] = {
    Try {
      val httpPost = new HttpPost(tokenEndpoint)
      httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded")
      
      val params = List(
        s"client_id=$clientId",
        s"client_secret=$clientSecret",
        s"grant_type=password",
        s"username=$username",
        s"password=$password"
      ).mkString("&")
      
      httpPost.setEntity(new StringEntity(params))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 200) {
        val tokenResponse = read[KeycloakTokenResponse](responseBody)
        
        decodeJwt(tokenResponse.access_token) match {
          case Some(info) =>
            Right(AuthResponse(
              access_token = tokenResponse.access_token,
              refresh_token = tokenResponse.refresh_token,
              expires_in = tokenResponse.expires_in,
              user_id = info.sub,
              username = info.preferred_username
            ))
          case None =>
            Left("Failed to decode token claims")
        }
      } else {
        Left(s"Login failed: $responseBody")
      }
    }.toEither match {
      case Right(result) => result
      case Left(e) => Left(e.getMessage)
    }
  }
  
  /** Register new user in Keycloak */
  def register(request: RegisterRequest): Either[String, String] = {
    Try {
      val httpPost = new HttpPost(usersEndpoint)
      httpPost.setHeader("Content-Type", "application/json")
      httpPost.setHeader("Authorization", s"Bearer $getAdminToken")
      
      val userJson = ujson.Obj(
        "username" -> request.username,
        "email" -> request.email,
        "firstName" -> request.firstName,
        "lastName" -> request.lastName,
        "enabled" -> true,
        "emailVerified" -> true,
        "credentials" -> ujson.Arr(
          ujson.Obj(
            "type" -> "password",
            "value" -> request.password,
            "temporary" -> false
          )
        ),
        "realmRoles" -> ujson.Arr("player")
      )
      
      httpPost.setEntity(new StringEntity(userJson.toString()))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 201) {
        // Extract user ID from Location header
        val locationHeader = response.getFirstHeader("Location")
        if (locationHeader != null) {
          val userId = locationHeader.getValue.split("/").last
          Right(userId)
        } else {
          Left("Failed to extract user ID from response")
        }
      } else {
        Left(s"Registration failed: $responseBody")
      }
    }.toEither match {
      case Right(result) => result
      case Left(e) => Left(e.getMessage)
    }
  }
  
  /** Refresh access token */
  def refreshToken(refreshToken: String): Either[String, AuthResponse] = {
    Try {
      val httpPost = new HttpPost(tokenEndpoint)
      httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded")
      
      val params = List(
        s"client_id=$clientId",
        s"client_secret=$clientSecret",
        s"grant_type=refresh_token",
        s"refresh_token=$refreshToken"
      ).mkString("&")
      
      httpPost.setEntity(new StringEntity(params))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 200) {
        val tokenResponse = read[KeycloakTokenResponse](responseBody)
        
        decodeJwt(tokenResponse.access_token) match {
          case Some(info) =>
            Right(AuthResponse(
              access_token = tokenResponse.access_token,
              refresh_token = tokenResponse.refresh_token,
              expires_in = tokenResponse.expires_in,
              user_id = info.sub,
              username = info.preferred_username
            ))
          case None =>
            Left("Failed to decode token claims")
        }
      } else {
        Left(s"Token refresh failed: $responseBody")
      }
    }.toEither match {
      case Right(result) => result
      case Left(e) => Left(e.getMessage)
    }
  }
  
  /** Decode JWT payload to extract user claims without a network call */
  def decodeJwt(accessToken: String): Option[KeycloakUserInfo] = {
    Try {
      val parts = accessToken.split("\\.")
      Option.when(parts.length >= 2) {
        val padded = parts(1).length % 4 match {
          case 0 => parts(1)
          case n => parts(1) + "=" * (4 - n)
        }
        val payload = new String(Base64.getUrlDecoder.decode(padded), "UTF-8")
        val json = ujson.read(payload)
        KeycloakUserInfo(
          sub                = json("sub").str,
          preferred_username = json("preferred_username").str,
          email              = json.obj.get("email").map(_.str).getOrElse(""),
          given_name         = json.obj.get("given_name").map(_.str).getOrElse(""),
          family_name        = json.obj.get("family_name").map(_.str).getOrElse(""),
          email_verified     = json.obj.get("email_verified").exists(_.bool)
        )
      }
    }.toOption.flatten
  }

  /** Validate access token and return user info if valid */
  def validateToken(accessToken: String): Either[String, KeycloakUserInfo] = {
    decodeJwt(accessToken) match {
      case Some(info) => Right(info)
      case None => Left("Invalid or expired token")
    }
  }
  
  /** Get admin token via master realm password grant */
  private def getAdminToken: String = {
    Try {
      val httpPost = new HttpPost(masterTokenEndpoint)
      httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded")
      
      val params = List(
        s"client_id=admin-cli",
        s"grant_type=password",
        s"username=$adminUsername",
        s"password=$adminPassword"
      ).mkString("&")
      
      httpPost.setEntity(new StringEntity(params))
      
      val response = httpClient.execute(httpPost)
      val responseBody = EntityUtils.toString(response.getEntity)
      
      if (response.getStatusLine.getStatusCode == 200) {
        val tokenResponse = read[KeycloakTokenResponse](responseBody)
        tokenResponse.access_token
      } else {
        throw new RuntimeException(s"Failed to get admin token: $responseBody")
      }
    }.getOrElse {
      throw new RuntimeException("Failed to get admin token")
    }
  }
  
  /** Close HTTP client */
  def close(): Unit = {
    Try(httpClient.close())
  }
}

// JSON codecs
given ReadWriter[KeycloakTokenResponse] = macroRW
given ReadWriter[KeycloakUserInfo] = macroRW
given ReadWriter[LoginRequest] = macroRW
given ReadWriter[RegisterRequest] = macroRW
given ReadWriter[AuthResponse] = macroRW
