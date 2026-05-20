package api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import controller.GameController
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import repository.GameRepository
import upickle.default.read

class ChessApiRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "test-system")

  val gameRepository = new GameRepository
  val gameController = new GameController()
  val sessionController = new GameSessionController(gameController, gameRepository)
  val routes = new ChessApiRoutes(sessionController).routes

  "Chess API Routes" should {

    "return API info on GET /v1/chess/info" in {
      Get("/v1/chess/info") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.GameInfos](responseAs[String])
        response.name shouldEqual "ME Chess REST API"
        response.version shouldEqual "1.0.0"
        response.status shouldEqual "running"
      }
    }

    "create a new game on POST /v1/chess/games" in {
      Post("/v1/chess/games") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        response.message shouldEqual "Game created successfully"
        response.gameId should not be empty
      }
    }

    "list all games on GET /v1/chess/games" in {
      Post("/v1/chess/games") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }

      Get("/v1/chess/games") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.GamesListResponse](responseAs[String])
        response.games should not be empty
        response.total should be > 0
      }
    }

    "get game state on GET /v1/chess/games/{gameId}" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Get(s"/v1/chess/games/$gameId") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.GameStateResponse](responseAs[String])
        response.gameId shouldEqual gameId
        response.isGameOver shouldEqual false
        response.moveHistory should be empty
      }
    }

    "return 404 for non-existent game" in {
      Get("/v1/chess/games/non-existent-game") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("Game not found")
      }
    }

    "apply a valid move on POST /v1/chess/games/{gameId}/moves" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      val moveRequest = """{"from": "e2", "to": "e4"}"""
      Post(s"/v1/chess/games/$gameId/moves", moveRequest) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.GameStateResponse](responseAs[String])
        response.moveHistory should have length 1
        response.turn should not be empty
      }
    }

    "reject invalid move request with empty body" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Post(s"/v1/chess/games/$gameId/moves", "") ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("cannot be empty")
      }
    }

    "reject invalid move with malformed JSON" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      val invalidJson = """{"invalid": "json"}"""
      Post(s"/v1/chess/games/$gameId/moves", invalidJson) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("Invalid JSON")
      }
    }

    "get game status on GET /v1/chess/games/{gameId}/status" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Get(s"/v1/chess/games/$gameId/status") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.GameStatusResponse](responseAs[String])
        response.gameId shouldEqual gameId
        response.isGameOver shouldEqual false
        response.moveCount shouldEqual 0
      }
    }

    "delete a game on DELETE /v1/chess/games/{gameId}" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Delete(s"/v1/chess/games/$gameId") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.ActionResponse](responseAs[String])
        response.message should include("deleted successfully")
      }

      Get(s"/v1/chess/games/$gameId") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return 404 when deleting non-existent game" in {
      Delete("/v1/chess/games/non-existent-game") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("Game not found")
      }
    }

    "save a game on POST /v1/chess/games/{gameId}/save" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Post(s"/v1/chess/games/$gameId/save") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = read[JsonCodecs.ActionResponse](responseAs[String])
        response.message should include("saved successfully")
      }
    }

    "return 404 when saving non-existent game" in {
      Post("/v1/chess/games/non-existent-game/save") ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("Game not found")
      }
    }

    "load a game on POST /v1/chess/games/load" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      Post(s"/v1/chess/games/$gameId/save") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }

      Post("/v1/chess/games/load") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        response.message should include("loaded successfully")
        response.gameId should not be empty
      }
    }

    "reject invalid game ID with empty string" in {
      Get("/v1/chess/games/") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "validate move request fields" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      val invalidMove = """{"from": "", "to": "e4"}"""
      Post(s"/v1/chess/games/$gameId/moves", invalidMove) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("cannot be empty")
      }
    }

    "handle move on completed game" in {
      var gameId = ""
      Post("/v1/chess/games") ~> routes ~> check {
        val response = read[JsonCodecs.CreatedGameResponse](responseAs[String])
        gameId = response.gameId
      }

      val state = sessionController.getGame(gameId).get
      val completedState = state.copy(board = state.board.copy(squares = Map.empty))
      sessionController.updateGame(gameId, completedState)

      val moveRequest = """{"from": "e2", "to": "e4"}"""
      Post(s"/v1/chess/games/$gameId/moves", moveRequest) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = read[JsonCodecs.ErrorResponse](responseAs[String])
        response.error should include("already over")
      }
    }
  }
}
