package presentation.rest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.io.StdIn
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContextExecutor

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
    implicit val system: ActorSystem[Nothing] = ActorSystem(akka.actor.typed.Behavior.empty, "chess-api")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val gameRepository = new GameRepository()
    val routes = new ChessApiRoutes(gameRepository).routes

    val bindingFuture = Http().newServerAt(host, port).bind(routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        val localAddress = binding.localAddress
        println(s"Server online at http://${localAddress.getHostString}:${localAddress.getPort}/")
        println(s"API Documentation: http://${localAddress.getHostString}:${localAddress.getPort}/v1/chess/info")
        println("Press ENTER to stop the server...")
        StdIn.readLine()
        binding.unbind().onComplete(_ => system.terminate())
      case Failure(exception) =>
        println(s"Failed to bind HTTP endpoint: ${exception.getMessage}")
        system.terminate()
    }
  }
}

