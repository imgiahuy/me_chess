package tournament.client.api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, Accept}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Framing, Source}
import akka.util.ByteString
import tournament.client.model._
import tournament.client.model.JsonFormats.{*, given}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** HTTP client for the external Tournament Server API.
  * Handles bearer tokens, JSON, form-urlencoded requests, and NDJSON streams.
  */
class TournamentApiClient(baseUrl: String)(implicit system: ActorSystem[?], ec: ExecutionContext) {
  
  private val http = Http()
  
  // Stored bearer token for authenticated requests
  private var authToken: Option[String] = None
  
  /** Set the bearer token for authenticated requests */
  def setToken(token: String): Unit = {
    authToken = Some(token)
  }
  
  /** Get the current bearer token */
  def getToken: Option[String] = authToken
  
  /** Clear the stored token */
  def clearToken(): Unit = {
    authToken = None
  }
  
  /** Create a request with optional authorization header */
  private def createRequest(
    method: HttpMethod,
    path: String,
    headers: Seq[HttpHeader] = Seq.empty,
    authenticated: Boolean = false
  ): HttpRequest = {
    val uri = s"$baseUrl$path"
    val authHeaders = if (authenticated && authToken.nonEmpty) {
      Seq(Authorization(OAuth2BearerToken(authToken.get)))
    } else {
      Seq.empty
    }
    
    HttpRequest(method = method, uri = uri, headers = headers ++ authHeaders)
  }
  
  /** Execute a request and unmarshal JSON response */
  private def executeRequest[T](
    request: HttpRequest,
    unmarshal: HttpResponse => Future[T]
  ): Future[T] = {
    http.singleRequest(request).flatMap { response =>
      if (response.status.isSuccess()) {
        unmarshal(response)
      } else {
        Unmarshal(response.entity).to[String].flatMap { body =>
          Future.failed(new Exception(s"Request failed with status ${response.status}: $body"))
        }
      }
    }
  }
  
  // --- Authentication ---
  
  /** Register a new identity (bot or user) and receive a JWT token */
  def register(request: RegisterRequest): Future[RegisterResponse] = {
    import upickle.default._
    
    val entity = HttpEntity(ContentTypes.`application/json`, write(request))
    val httpRequest = createRequest(HttpMethods.POST, "/api/auth/register")
      .withEntity(entity)
    
    executeRequest(httpRequest, response => Unmarshal(response.entity).to[String].map { body =>
      read[RegisterResponse](body)
    })
  }
  
  // --- Tournament Discovery ---
  
  /** List all tournaments grouped by status */
  def listTournaments(): Future[TournamentListResponse] = {
    import upickle.default._
    
    val request = createRequest(HttpMethods.GET, "/api/tournament")
    
    executeRequest(request, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentListResponse](body)
    })
  }
  
  /** Get details of a specific tournament */
  def getTournament(tournamentId: String): Future[TournamentDetail] = {
    import upickle.default._
    
    val request = createRequest(HttpMethods.GET, s"/api/tournament/$tournamentId")
    
    executeRequest(request, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentDetail](body)
    })
  }
  
  // --- Tournament Creation ---
  
  /** Create a new tournament (requires director token) */
  def createTournament(
    name: String,
    nbRounds: Int,
    clockLimit: Int,
    clockIncrement: Int = 0,
    rated: Boolean = false,
    format: String = "roundrobin",
    startPosition: Option[String] = None,
    matchesPerPairing: Int = 1,
    groupSize: Int = 2,
    opening: Option[String] = None,
    bots: Option[String] = None,
    maxConcurrentGames: Int = 1
  )(implicit directorToken: String): Future[TournamentDetail] = {
    import upickle.default._
    
    val formData = FormData(
      Map(
        "name" -> name,
        "nbRounds" -> nbRounds.toString,
        "clockLimit" -> clockLimit.toString,
        "clockIncrement" -> clockIncrement.toString,
        "rated" -> rated.toString,
        "format" -> format,
        "matchesPerPairing" -> matchesPerPairing.toString,
        "groupSize" -> groupSize.toString,
        "maxConcurrentGames" -> maxConcurrentGames.toString
      ) ++ startPosition.map("startPosition" -> _) ++ opening.map("opening" -> _) ++ bots.map("bots" -> _)
    )
    
    val entity = formData.toEntity
    val httpRequest = createRequest(HttpMethods.POST, "/api/tournament", authenticated = true)
      .withEntity(entity)
    
    executeRequest(httpRequest, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentDetail](body)
    })
  }
  
  // --- Tournament Participation ---
  
  /** Start a tournament (requires director token) */
  def startTournament(tournamentId: String)(implicit directorToken: String): Future[TournamentDetail] = {
    import upickle.default._
    
    val request = createRequest(HttpMethods.POST, s"/api/tournament/$tournamentId/start", authenticated = true)
    
    executeRequest(request, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentDetail](body)
    })
  }
  
  /** Join a tournament as a bot (requires bot token) */
  def joinTournament(tournamentId: String): Future[TournamentDetail] = {
    import upickle.default._
    
    val request = createRequest(HttpMethods.POST, s"/api/tournament/$tournamentId/join", authenticated = true)
    
    executeRequest(request, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentDetail](body)
    })
  }
  
  /** Register a bot (requires director token) */
  def registerBot(request: RegisterBotRequest)(implicit directorToken: String): Future[RegisterBotResponse] = {
    import upickle.default._
    
    val entity = HttpEntity(ContentTypes.`application/json`, write(request))
    val httpRequest = createRequest(HttpMethods.POST, "/api/bots", authenticated = true)
      .withEntity(entity)
    
    executeRequest(httpRequest, response => Unmarshal(response.entity).to[String].map { body =>
      read[RegisterBotResponse](body)
    })
  }
  
  /** Add a registered bot to a tournament (requires director token) */
  def addParticipant(tournamentId: String, request: AddParticipantRequest)(implicit directorToken: String): Future[TournamentDetail] = {
    import upickle.default._
    
    val entity = HttpEntity(ContentTypes.`application/json`, write(request))
    val httpRequest = createRequest(HttpMethods.POST, s"/api/tournament/$tournamentId/participants", authenticated = true)
      .withEntity(entity)
    
    executeRequest(httpRequest, response => Unmarshal(response.entity).to[String].map { body =>
      read[TournamentDetail](body)
    })
  }
  
  // --- Move Submission ---
  
  /** Submit a move for a game (requires bot token) */
  def submitMove(tournamentId: String, gameId: String, uci: String): Future[Unit] = {
    val request = createRequest(HttpMethods.POST, s"/api/tournament/$tournamentId/game/$gameId/move/$uci", authenticated = true)
    
    executeRequest(request, _ => Future.successful(()))
  }
  
  // --- NDJSON Streaming ---
  
  /** Open tournament event stream (NDJSON) */
  def openTournamentStream(tournamentId: String): Source[TournamentEvent, Any] = {
    val request = createRequest(
      HttpMethods.GET,
      s"/api/tournament/$tournamentId/stream",
      headers = Seq(Accept(MediaTypes.`application/json`)),
      authenticated = true
    )
    
    Source.futureSource(
      http.singleRequest(request).flatMap { response =>
        if (response.status.isSuccess()) {
          Future.successful(response.entity.dataBytes)
        } else {
          Unmarshal(response.entity).to[String].flatMap { body =>
            Future.failed(new Exception(s"Stream request failed with status ${response.status}: $body"))
          }
        }
      }
    ).via(Framing.delimiter(ByteString("\n"), 10000, allowTruncation = true))
     .map(_.utf8String)
     .filter(_.nonEmpty)
     .map(parseTournamentEvent)
     .collect { case Some(event) => event }
  }
  
  /** Open game event stream (NDJSON) */
  def openGameStream(tournamentId: String, gameId: String): Source[GameEvent, Any] = {
    val request = createRequest(
      HttpMethods.GET,
      s"/api/tournament/$tournamentId/game/$gameId/stream",
      headers = Seq(Accept(MediaTypes.`application/json`)),
      authenticated = true
    )
    
    Source.futureSource(
      http.singleRequest(request).flatMap { response =>
        if (response.status.isSuccess()) {
          Future.successful(response.entity.dataBytes)
        } else {
          Unmarshal(response.entity).to[String].flatMap { body =>
            Future.failed(new Exception(s"Stream request failed with status ${response.status}: $body"))
          }
        }
      }
    ).via(Framing.delimiter(ByteString("\n"), 10000, allowTruncation = true))
     .map(_.utf8String)
     .filter(_.nonEmpty)
     .map(parseGameEvent)
     .collect { case Some(event) => event }
  }
  
  /** Parse a tournament event from JSON */
  private def parseTournamentEvent(json: String): Option[TournamentEvent] = {
    import upickle.default._
    
    Try {
      val eventType = read[String](json).split("\"type\":\"")(1).split("\"")(0)
      eventType match {
        case "tournamentStarted" => Some(read[TournamentStartedEvent](json))
        case "roundStarted" => Some(read[RoundStartedEvent](json))
        case "gameStart" => Some(read[GameStartEvent](json))
        case "roundFinished" => Some(read[RoundFinishedEvent](json))
        case "tournamentFinished" => Some(read[TournamentFinishedEvent](json))
        case "heartbeat" => Some(read[HeartbeatEvent](json))
        case _ => None
      }
    }.getOrElse(None)
  }
  
  /** Parse a game event from JSON */
  private def parseGameEvent(json: String): Option[GameEvent] = {
    import upickle.default._
    
    Try {
      val eventType = read[String](json).split("\"type\":\"")(1).split("\"")(0)
      eventType match {
        case "gameState" => Some(read[GameStateEvent](json))
        case "move" => Some(read[MoveEvent](json))
        case "gameEnd" => Some(read[GameEndEvent](json))
        case "heartbeat" => Some(read[GameHeartbeatEvent](json))
        case _ => None
      }
    }.getOrElse(None)
  }
  
  /** Shutdown the HTTP client */
  def shutdown(): Future[Unit] = {
    http.shutdownAllConnectionPools()
  }
}

object TournamentApiClient {
  /** Create a new API client */
  def apply(baseUrl: String)(implicit system: ActorSystem[?], ec: ExecutionContext): TournamentApiClient = {
    new TournamentApiClient(baseUrl)
  }
}
