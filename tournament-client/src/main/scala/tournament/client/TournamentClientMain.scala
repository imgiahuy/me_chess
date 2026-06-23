package tournament.client

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import tournament.client.api.TournamentApiClient
import tournament.client.runner.TournamentBotRunner
import tournament.client.model._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Main entry point for the Tournament Client CLI.
  * Supports registering bots, joining tournaments, and participating in tournament games.
  */
object TournamentClientMain {
  
  def main(args: Array[String]): Unit = {
    val command = args.headOption.getOrElse("help")
    
    command match {
      case "help" => printHelp()
      case "register" => handleRegister(args.drop(1))
      case "join" => handleJoin(args.drop(1))
      case "run" => handleRun(args.drop(1))
      case "list" => handleList(args.drop(1))
      case _ =>
        println(s"Unknown command: $command")
        printHelp()
        System.exit(1)
    }
  }
  
  /** Print help information */
  private def printHelp(): Unit = {
    println("""
      |Tournament Client CLI
      |
      |Usage: java -jar chess-tournament-client.jar <command> [options]
      |
      |Commands:
      |  help                    Show this help message
      |  register <name>        Register a new bot and get JWT token
      |  list                   List all tournaments
      |  join <tournament-id>   Join a tournament with registered bot
      |  run <tournament-id>    Register, join, and participate in a tournament
      |
      |Options:
      |  --url <url>            Tournament server URL (default: http://localhost:8080)
      |  --bot-type <type>      Bot type: stockfish, stockfish-easy, stockfish-medium, random, capture (default: stockfish)
      |  --token <token>        JWT token (for join command)
      |
      |Examples:
      |  # Register a new bot
      |  java -jar chess-tournament-client.jar register MyBot
      |
      |  # List tournaments
      |  java -jar chess-tournament-client.jar list --url http://localhost:8080
      |
      |  # Join a tournament (requires token)
      |  java -jar chess-tournament-client.jar join abc123 --token eyJhbGc...
      |
      |  # Run in a tournament (auto-register and join)
      |  java -jar chess-tournament-client.jar run abc123 --url http://localhost:8080 --bot-type stockfish
      |
      |Environment Variables:
      |  TOURNAMENT_SERVER_URL  Default tournament server URL
      |  BOT_NAME               Default bot name
      |  BOT_TYPE               Default bot type
      """.stripMargin)
  }
  
  /** Parse command line options */
  private def parseOptions(args: Array[String]): Map[String, String] = {
    var options = Map.empty[String, String]
    var i = 0
    while (i < args.length) {
      if (args(i).startsWith("--")) {
        val key = args(i).substring(2)
        if (i + 1 < args.length && !args(i + 1).startsWith("--")) {
          options = options + (key -> args(i + 1))
          i += 2
        } else {
          options = options + (key -> "true")
          i += 1
        }
      } else {
        i += 1
      }
    }
    options
  }
  
  /** Get tournament server URL from options or environment */
  private def getServerUrl(options: Map[String, String]): String = {
    options.getOrElse("url", sys.env.getOrElse("TOURNAMENT_SERVER_URL", "http://localhost:8080"))
  }
  
  /** Get bot type from options or environment */
  private def getBotType(options: Map[String, String]): String = {
    options.getOrElse("bot-type", sys.env.getOrElse("BOT_TYPE", "stockfish"))
  }
  
  /** Handle register command */
  private def handleRegister(args: Array[String]): Unit = {
    val botName = args.headOption.getOrElse(sys.env.getOrElse("BOT_NAME", "DefaultBot"))
    val options = parseOptions(args.drop(1))
    val serverUrl = getServerUrl(options)
    
    println(s"[INFO] Registering bot '$botName' with server at $serverUrl")
    
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-client")
    implicit val ec: ExecutionContext = system.executionContext
    
    val apiClient = TournamentApiClient(serverUrl)
    
    val request = RegisterRequest(name = botName, isBot = true)
    
    apiClient.register(request).onComplete {
      case Success(response) =>
        println(s"[SUCCESS] Bot registered successfully!")
        println(s"  Bot ID: ${response.id}")
        println(s"  Token: ${response.token}")
        println(s"\nSave this token for future operations:")
        println(s"  --token ${response.token}")
        apiClient.shutdown()
        system.terminate()
        System.exit(0)
        
      case Failure(error) =>
        println(s"[ERROR] Failed to register bot: ${error.getMessage}")
        apiClient.shutdown()
        system.terminate()
        System.exit(1)
    }
    
    // Keep the system alive
    Thread.sleep(Long.MaxValue)
  }
  
  /** Handle list command */
  private def handleList(args: Array[String]): Unit = {
    val options = parseOptions(args)
    val serverUrl = getServerUrl(options)
    
    println(s"[INFO] Listing tournaments from server at $serverUrl")
    
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-client")
    implicit val ec: ExecutionContext = system.executionContext
    
    val apiClient = TournamentApiClient(serverUrl)
    
    apiClient.listTournaments().onComplete {
      case Success(response) =>
        println(s"\n[SUCCESS] Tournament list retrieved:")
        println(s"\nCreated (${response.created.length}):")
        response.created.foreach { t =>
          println(s"  - ${t.id}: ${t.name} (${t.format}, ${t.rounds} rounds)")
        }
        println(s"\nStarted (${response.started.length}):")
        response.started.foreach { t =>
          println(s"  - ${t.id}: ${t.name} (${t.format}, round ${t.currentRound}/${t.rounds})")
        }
        println(s"\nFinished (${response.finished.length}):")
        response.finished.foreach { t =>
          println(s"  - ${t.id}: ${t.name} (${t.format})")
        }
        apiClient.shutdown()
        system.terminate()
        System.exit(0)
        
      case Failure(error) =>
        println(s"[ERROR] Failed to list tournaments: ${error.getMessage}")
        apiClient.shutdown()
        system.terminate()
        System.exit(1)
    }
    
    Thread.sleep(Long.MaxValue)
  }
  
  /** Handle join command */
  private def handleJoin(args: Array[String]): Unit = {
    val tournamentId = args.headOption.getOrElse {
      println("[ERROR] Tournament ID is required for join command")
      System.exit(1)
      ""
    }
    
    val options = parseOptions(args.drop(1))
    val serverUrl = getServerUrl(options)
    val token = options.getOrElse("token", sys.env.getOrElse("TOURNAMENT_TOKEN", ""))
    
    if (token.isEmpty) {
      println("[ERROR] Token is required for join command. Use --token or TOURNAMENT_TOKEN environment variable.")
      System.exit(1)
    }
    
    println(s"[INFO] Joining tournament $tournamentId")
    
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-client")
    implicit val ec: ExecutionContext = system.executionContext
    
    val apiClient = TournamentApiClient(serverUrl)
    apiClient.setToken(token)
    
    apiClient.joinTournament(tournamentId).onComplete {
      case Success(tournament) =>
        println(s"[SUCCESS] Joined tournament '${tournament.name}'")
        println(s"  Status: ${tournament.status}")
        println(s"  Format: ${tournament.format}")
        println(s"  Rounds: ${tournament.rounds}")
        println(s"  Participants: ${tournament.participants.length}")
        apiClient.shutdown()
        system.terminate()
        System.exit(0)
        
      case Failure(error) =>
        println(s"[ERROR] Failed to join tournament: ${error.getMessage}")
        apiClient.shutdown()
        system.terminate()
        System.exit(1)
    }
    
    Thread.sleep(Long.MaxValue)
  }
  
  /** Handle run command (register, join, and participate) */
  private def handleRun(args: Array[String]): Unit = {
    val tournamentId = args.headOption.getOrElse {
      println("[ERROR] Tournament ID is required for run command")
      System.exit(1)
      ""
    }
    
    val options = parseOptions(args.drop(1))
    val serverUrl = getServerUrl(options)
    val botName = sys.env.getOrElse("BOT_NAME", "StockfishBot")
    val botType = getBotType(options)
    
    println(s"[INFO] Starting tournament participation")
    println(s"  Server: $serverUrl")
    println(s"  Tournament: $tournamentId")
    println(s"  Bot: $botName ($botType)")
    
    implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-bot")
    implicit val ec: ExecutionContext = system.executionContext
    
    val runner = TournamentBotRunner(serverUrl, botName, botType)
    
    // Register, join, and participate
    val participation = for {
      _ <- runner.register()
      _ <- runner.joinTournament(tournamentId)
      _ <- runner.participate(tournamentId)
    } yield {
      println("[INFO] Tournament participation completed")
      runner.shutdown()
      system.terminate()
      System.exit(0)
    }
    
    participation.onComplete {
      case Failure(error) =>
        println(s"[ERROR] Tournament participation failed: ${error.getMessage}")
        runner.shutdown()
        system.terminate()
        System.exit(1)
    }
    
    // Keep the system alive
    Thread.sleep(Long.MaxValue)
  }
}
