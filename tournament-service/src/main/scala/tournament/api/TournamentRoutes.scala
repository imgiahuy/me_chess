package tournament.api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import tournament.engine.TournamentManager
import tournament.kafka.TournamentKafkaService
import tournament.model._
import tournament.persistence.TournamentRepository
import tournament.auth.JwtService
import shared.http.HttpHelpers

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** REST API routes for the tournament service. */
class TournamentRoutes(repo: TournamentRepository, kafka: TournamentKafkaService, jwtService: JwtService)(implicit system: ActorSystem[?]):

  import upickle.default.{ReadWriter, macroRW, read, write, given}
  import JsonFormats.{*, given}
  import HttpHelpers.{jsonResponse, parseJson}

  private val manager = new TournamentManager()

  val routes: Route = pathPrefix("v1" / "tournaments") {
    concat(
      // ─── Authentication ───────────────────────────────────────────────────────
      path("auth" / "register") {
        post {
          entity(as[String]) { body =>
            parseJson[BotAuthRequest](body) match
              case Right(req) =>
                jwtService.generateToken(req.botId, req.botName, req.botFamily) match
                  case Right(token) =>
                    complete(StatusCodes.OK, jsonResponse(BotAuthResponse(token, req.botId, req.botName)))
                  case Left(err) =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
              case Left(err) =>
                complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
          }
        }
      },
      path("auth" / "validate") {
        post {
          entity(as[String]) { body =>
            parseJson[TokenValidationRequest](body) match
              case Right(req) =>
                jwtService.validateToken(req.token) match
                  case Right(claims) =>
                    complete(StatusCodes.OK, jsonResponse(TokenValidationResponse(true, Some(claims.sub), Some(claims.name))))
                  case Left(err) =>
                    complete(StatusCodes.Unauthorized, jsonResponse(TokenValidationResponse(false, None, None, Some(err))))
              case Left(err) =>
                complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
          }
        }
      },
      // ─── Tournament Management ───────────────────────────────────────────────
      pathEnd {
        concat(
          get {
            complete(StatusCodes.OK, jsonResponse(repo.findAll().map(toSummary)))
          },
          post {
            entity(as[String]) { body =>
              parseJson[CreateTournamentRequest](body) match
                case Right(req) =>
                  parseFormat(req.format) match
                    case Some(format) =>
                      val config = TournamentConfig(
                        name = req.name,
                        format = format,
                        rounds = req.rounds,
                        gamesPerPairing = req.gamesPerPairing.getOrElse(1),
                        timeControlSeconds = req.timeControlSeconds.getOrElse(300),
                        incrementSeconds = req.incrementSeconds.getOrElse(3),
                        description = req.description
                      )
                      val tournament = Tournament.create(config)
                      repo.save(tournament)
                      kafka.publishTournamentCreated(tournament.id, tournament.name, tournament.format.toString)
                      complete(StatusCodes.Created, jsonResponse(tournament))
                    case None =>
                      complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(s"Invalid format: ${req.format}")))
                case Left(err) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
            }
          }
        )
      },
      path(Segment) { id =>
        concat(
          get {
            repo.findById(id) match
              case Some(t) => complete(StatusCodes.OK, jsonResponse(t))
              case None => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
          },
          delete {
            if repo.delete(id) then complete(StatusCodes.NoContent)
            else complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
          }
        )
      },
      path(Segment / "register") { id =>
        post {
          entity(as[String]) { body =>
            parseJson[RegisterParticipantRequest](body) match
              case Right(req) =>
                repo.findById(id) match
                  case Some(t) =>
                    val participant = Participant(
                      id = UUID.randomUUID().toString,
                      name = req.name,
                      botType = req.botType
                    )
                    manager.register(t, participant) match
                      case Right(updated) =>
                        repo.save(updated)
                        kafka.publishParticipantRegistered(updated.id, participant.id, participant.name)
                        complete(StatusCodes.OK, jsonResponse(updated))
                      case Left(err) =>
                        complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
                  case None =>
                    complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
              case Left(err) =>
                complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
          }
        }
      },
      path(Segment / "start") { id =>
        post {
          repo.findById(id) match
            case Some(t) =>
              manager.start(t) match
                case Right(updated) =>
                  repo.save(updated)
                  kafka.publishTournamentStarted(updated.id)
                  complete(StatusCodes.OK, jsonResponse(updated))
                case Left(err) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
            case None =>
              complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
        }
      },
      path(Segment / "finish") { id =>
        post {
          repo.findById(id) match
            case Some(t) =>
              manager.finish(t) match
                case Right(updated) =>
                  repo.save(updated)
                  kafka.publishTournamentFinished(updated.id)
                  complete(StatusCodes.OK, jsonResponse(updated))
                case Left(err) =>
                  complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
            case None =>
              complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
        }
      },
      path(Segment / "result") { id =>
        post {
          entity(as[String]) { body =>
            parseJson[ReportResultRequest](body) match
              case Right(req) =>
                GameResult.fromString(req.result) match
                  case Some(result) =>
                    repo.findById(id) match
                      case Some(t) =>
                        manager.reportResult(t, req.gameId, result) match
                          case Right(updated) =>
                            repo.save(updated)
                            kafka.publishGameResultReported(updated.id, req.gameId, result.toString)
                            if updated.status == TournamentStatus.Finished then
                              kafka.publishTournamentFinished(updated.id)
                            complete(StatusCodes.OK, jsonResponse(updated))
                          case Left(err) =>
                            complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
                      case None =>
                        complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
                  case None =>
                    complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(s"Invalid result: ${req.result}")))
              case Left(err) =>
                complete(StatusCodes.BadRequest, jsonResponse(ErrorResponse(err)))
          }
        }
      },
      path(Segment / "standings") { id =>
        get {
          repo.findById(id) match
            case Some(t) => complete(StatusCodes.OK, jsonResponse(t.standings))
            case None => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
        }
      },
      path(Segment / "pairings") { id =>
        get {
          repo.findById(id) match
            case Some(t) => complete(StatusCodes.OK, jsonResponse(t.roundsData.flatMap(_.pairings)))
            case None => complete(StatusCodes.NotFound, jsonResponse(ErrorResponse(s"Tournament not found: $id")))
        }
      }
    )
  } ~ path("health") {
    get {
      complete(StatusCodes.OK, jsonResponse(HealthResponse("UP", "tournament-service")))
    }
  }

  private def toSummary(t: Tournament): TournamentSummary =
    TournamentSummary(
      id = t.id,
      name = t.name,
      format = t.format.toString,
      status = t.status.toString,
      participantCount = t.participants.size,
      rounds = t.rounds,
      currentRound = t.roundsData.lastOption.map(_.number).getOrElse(0),
      createdAt = t.createdAt
    )

  private def parseFormat(s: String): Option[TournamentFormat] =
    TournamentFormat.fromString(s)

  case class ErrorResponse(error: String)
  case class HealthResponse(status: String, service: String)

  private object JsonFormats:
    given ReadWriter[Instant] = upickle.default.readwriter[String].bimap(
      i => i.toString,
      s => Instant.parse(s)
    )
    given ReadWriter[TournamentFormat] = upickle.default.readwriter[String].bimap(
      e => e.toString,
      s => TournamentFormat.valueOf(s)
    )
    given ReadWriter[TournamentStatus] = upickle.default.readwriter[String].bimap(
      e => e.toString,
      s => TournamentStatus.valueOf(s)
    )
    given ReadWriter[GameResult] = upickle.default.readwriter[String].bimap(
      e => e.toString,
      s => GameResult.fromString(s).getOrElse(throw new IllegalArgumentException(s"Unknown GameResult: $s"))
    )
    given ReadWriter[Tournament] = macroRW
    given ReadWriter[Participant] = macroRW
    given ReadWriter[Pairing] = macroRW
    given ReadWriter[Round] = macroRW
    given ReadWriter[Standing] = macroRW
    given ReadWriter[TournamentConfig] = macroRW
    given ReadWriter[CreateTournamentRequest] = macroRW
    given ReadWriter[RegisterParticipantRequest] = macroRW
    given ReadWriter[ReportResultRequest] = macroRW
    given ReadWriter[TournamentSummary] = macroRW
    given ReadWriter[ErrorResponse] = macroRW
    given ReadWriter[HealthResponse] = macroRW
    given ReadWriter[BotAuthRequest] = macroRW
    given ReadWriter[BotAuthResponse] = macroRW
    given ReadWriter[TokenValidationRequest] = macroRW
    given ReadWriter[TokenValidationResponse] = macroRW
