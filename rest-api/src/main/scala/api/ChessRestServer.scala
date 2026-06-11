package api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import controller.GameController
import database.DatabaseManager
import repository.DatabaseGameRepository


import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Failure, Success}

/** REST API server for the Chess application.
 *
 * Starts an HTTP server on the configured host and port,
 * providing REST endpoints for game management and play.
 */
object ChessRestServer {

  def main(args: Array[String]): Unit = {
    run()
  }

  def run(host: String = "0.0.0.0", port: Int = 8080): Unit = {
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty[Unit], "chess-api")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    try {
      println("[INFO] Initializing Chess REST API Server...")
      println("[INFO] Creating game controller and database...")
      
      val controller = new GameController()

      //val gameRepository = new InMemoryGameRepository()

      //val dbManager = DatabaseManager.postgresql(
        //host = "localhost",
        //port = 5432,
        //database = "chess",
       // user = "chess_user",
     //   password = "chess_password"
      //)

      // Initialize database schema
     // println("[INFO] Initializing database schema...")
     // dbManager.initializeSchema()

      val dbManager = DatabaseManager.mongodb(
        host = sys.env.getOrElse("MONGODB_HOST", "localhost"),
        port = sys.env.getOrElse("MONGODB_PORT", "27017").toInt,
        database = sys.env.getOrElse("MONGODB_DATABASE", "chess")
      )

      // Initialize database schema
      println("[INFO] Initializing database schema...")
      dbManager.initializeSchema()
      
      // Create database-backed repository
      val gameRepository = new DatabaseGameRepository(dbManager.gameDao)
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
          println("[INFO] Database: MongoDB (games will persist in database)")
          println("============================================================")
          println("[INFO] Server running continuously...")
          println("")

          // Add a hook to keep the server running
          sys.addShutdownHook {
            println("[INFO] Shutting down server...")
            binding.unbind().onComplete { _ =>
              dbManager.close()
              println("[INFO] Database closed")
              println("[INFO] Server stopped")
              system.terminate()
            }
          }

          // Keep the server running indefinitely
          while (true) {
            Thread.sleep(1000)
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

