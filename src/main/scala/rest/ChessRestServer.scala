package rest

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.io.StdIn
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

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
      val gameRepository = new GameRepository()
      val routes = new ChessApiRoutes(gameRepository).routes

      val bindingFuture = Http().newServerAt(host, port).bind(routes)

      bindingFuture.onComplete {
        case Success(binding) =>
          val localAddress = binding.localAddress
          println("")
          println("============================================================")
          println("[SUCCESS] Server online at http://" + localAddress.getHostString + ":" + localAddress.getPort + "/")
          println("[INFO] API Documentation: http://" + localAddress.getHostString + ":" + localAddress.getPort + "/v1/chess/info")
          println("============================================================")
          println("[INFO] Press ENTER to stop the server...")
          println("")

          // Keep the application running
          StdIn.readLine()

          println("[INFO] Shutting down server...")
          binding.unbind().onComplete { _ =>
            system.terminate()
          }
        case Failure(exception) =>
          println("")
          println("[ERROR] Failed to bind HTTP endpoint on " + host + ":" + port)
          println("[ERROR] " + exception.getMessage)
          println("")
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

