package lichess

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import shared.service.ServiceBootstrap

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/** Entry point for the Lichess bot microservice.
  *
  * The bot streams the Lichess account event API, accepts configured challenges,
  * and plays games using the internal chess engine bots.
  *
  * Required environment variable:
  *   - LICHESS_API_TOKEN: Lichess API token for a BOT account
  *
  * Optional environment variables:
  *   - LICHESS_BASE_URL            (default https://lichess.org)
  *   - BOT_TYPE                    (default greedy)
  *   - ALLOW_RATED                 (default true)
  *   - ALLOW_UNRATED               (default true)
  *   - ALLOWED_SPEEDS              (default bullet,blitz,rapid,classical)
  *   - ALLOWED_VARIANTS            (default standard)
  *   - MAX_INITIAL_TIME_SECONDS    (default Int.MaxValue)
  *   - MIN_INCREMENT_SECONDS       (default 0)
  */
object LichessBotMain {

  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    implicit val system: ActorSystem[Unit] = ServiceBootstrap.createActorSystem("lichess-bot")
    implicit val ec = system.executionContext

    val config = loadConfig() match {
      case Right(cfg) => cfg
      case Left(errors) =>
        println("[LichessBotMain] Configuration errors:")
        errors.foreach(println)
        sys.exit(1)
    }

    println(s"[LichessBotMain] Starting Lichess bot at ${config.baseUrl}")
    println(s"[LichessBotMain] Bot type: ${config.botType}")
    println(s"[LichessBotMain] Allowed speeds: ${config.challengeFilter.allowedSpeeds.mkString(",")}")

    val client = new LichessClient(config)
    val worker = new LichessBotWorker(client, config)

    val healthHost = sys.env.getOrElse("BOT_HEALTH_HOST", "0.0.0.0")
    val healthPort = Try(sys.env.getOrElse("BOT_HEALTH_PORT", "8080").toInt).getOrElse(8080)
    val healthServer = new LichessBotHealthServer(worker, healthHost, healthPort)
    val healthBindingFuture = healthServer.start()

    val workerFuture = worker.run()

    sys.addShutdownHook {
      println("[LichessBotMain] Shutting down...")
      Try(Await.result(worker.shutdown(), 10.seconds))
      Try(Await.result(healthBindingFuture.flatMap(_.unbind()), 10.seconds))
      system.terminate()
    }

    healthBindingFuture.onComplete {
      case Success(binding) =>
        println(s"[LichessBotMain] Health server online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/health")
      case Failure(ex) =>
        println(s"[LichessBotMain] Warning: failed to start health server: ${ex.getMessage}")
    }

    Await.result(workerFuture, Duration.Inf)
  }

  private def loadConfig(): Either[List[String], LichessBotConfig] = {
    val token = sys.env.get("LICHESS_API_TOKEN")
    val baseUrl = sys.env.getOrElse("LICHESS_BASE_URL", "https://lichess.org")
    val botType = sys.env.getOrElse("BOT_TYPE", "greedy")

    val errors = token.filter(_.trim.nonEmpty) match {
      case None => List("LICHESS_API_TOKEN environment variable is required")
      case Some(_) => Nil
    }

    if (errors.nonEmpty) {
      return Left(errors)
    }

    val allowRated    = parseBoolean(sys.env.getOrElse("ALLOW_RATED", "true"), true)
    val allowUnrated  = parseBoolean(sys.env.getOrElse("ALLOW_UNRATED", "true"), true)
    val allowedSpeeds = sys.env.getOrElse("ALLOWED_SPEEDS", "bullet,blitz,rapid,classical").split(",").map(_.trim).filter(_.nonEmpty).toSet
    val allowedVariants = sys.env.getOrElse("ALLOWED_VARIANTS", "standard").split(",").map(_.trim).filter(_.nonEmpty).toSet
    val maxInitialTime = Try(sys.env.getOrElse("MAX_INITIAL_TIME_SECONDS", Int.MaxValue.toString).toInt).getOrElse(Int.MaxValue)
    val minIncrement   = Try(sys.env.getOrElse("MIN_INCREMENT_SECONDS", "0").toInt).getOrElse(0)

    val filter = ChallengeFilter(
      allowRated = allowRated,
      allowUnrated = allowUnrated,
      allowedSpeeds = allowedSpeeds,
      allowedVariants = allowedVariants,
      maxInitialTimeSeconds = maxInitialTime,
      minIncrementSeconds = minIncrement
    )

    Right(LichessBotConfig(
      apiToken = token.get,
      baseUrl = baseUrl,
      botType = botType,
      challengeFilter = filter
    ))
  }

  private def parseBoolean(value: String, default: Boolean): Boolean = {
    value.trim.toLowerCase match {
      case "true" | "1" | "yes" => true
      case "false" | "0" | "no" => false
      case _ => default
    }
  }
}
