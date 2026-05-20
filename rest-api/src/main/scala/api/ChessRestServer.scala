package api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import controller.GameController

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}
import repository.GameRepository

/** REST API server for the Chess application.
 *
 * Starts an HTTP server on the configured host and port,
 * providing REST endpoints for game management and play.
 */
object ChessRestServer {

  def main(args: Array[String]): Unit = {
    run()
  }

  def run(host: String = "localhost", port: Int = 8080): Unit = {
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "chess-api")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    try {
      println("[INFO] Initializing Chess REST API Server...")
      println("[INFO] Creating game controller and repository...")
      
      val controller = new GameController()
      val gameRepository = new GameRepository
      val sessionController = new GameSessionController(controller, gameRepository)

      println("[INFO] Setting up API routes...")
      val chessRoutes = new ChessApiRoutes(sessionController).routes
      val allRoutes: Route = Directives.concat(chessRoutes)

      println("[INFO] Starting HTTP server on $host:$port...")
      val bindingFuture = Http().newServerAt(host, port).bind(allRoutes)

      bindingFuture.onComplete {
        case Success(binding) =>
          val localAddress = binding.localAddress
          val serverUrl = s"http://${localAddress.getHostString}:${localAddress.getPort}"
          
          println("")
          println("============================================================")
          println("[SUCCESS] Server online at " + serverUrl)
          println("[INFO] Chess API: " + serverUrl + "/v1/chess/info")
          println("[INFO] Create Game: POST " + serverUrl + "/v1/chess/games")
          println("[INFO] List Games: GET " + serverUrl + "/v1/chess/games")
          println("============================================================")
          println("[INFO] Press ENTER to stop the server...")
          println("")

          StdIn.readLine()

          println("[INFO] Shutting down server...")
          binding.unbind().onComplete { _ =>
            println("[INFO] Server stopped")
            system.terminate()
          }
        case Failure(exception) =>
          println("")
          println("[ERROR] Failed to bind HTTP endpoint on " + host + ":" + port)
          println("[ERROR] " + exception.getMessage)
          println("[ERROR] Possible causes:")
          println("[ERROR]   - Port " + port + " is already in use")
          println("[ERROR]   - Insufficient permissions to bind to port " + port)
          println("[ERROR]   - Invalid host address: " + host)
          println("")
          exception.printStackTrace()
          system.terminate()
      }
    } catch {
      case e: Exception =>
        println("")
        println("[ERROR] Error starting server: " + e.getMessage)
        println("")
        e.printStackTrace()
        system.terminate()
    }
  }
}

