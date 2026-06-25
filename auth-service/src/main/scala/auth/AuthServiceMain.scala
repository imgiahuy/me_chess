package auth

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Success, Failure}
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

object AuthServiceMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)
    
    // Load configuration from environment variables
    val keycloakUrl = sys.env.getOrElse("KEYCLOAK_URL", "http://keycloak:8080")
    val keycloakRealm = sys.env.getOrElse("KEYCLOAK_REALM", "chess")
    val keycloakClientId = sys.env.getOrElse("KEYCLOAK_CLIENT_ID", "chess-api")
    val keycloakClientSecret = sys.env.getOrElse("KEYCLOAK_CLIENT_SECRET", "chess-api-secret")
    val keycloakAdminUsername = sys.env.getOrElse("KEYCLOAK_ADMIN", "admin")
    val keycloakAdminPassword = sys.env.getOrElse("KEYCLOAK_ADMIN_PASSWORD", "admin")
    val playerServiceUrl = sys.env.getOrElse("PLAYER_SERVICE_URL", "http://player-service:8090")
    val servicePort = sys.env.getOrElse("AUTH_SERVICE_PORT", "8088").toInt
    
    logger.info(s"Starting Auth Service on port $servicePort")
    logger.info(s"Keycloak URL: $keycloakUrl")
    logger.info(s"Keycloak Realm: $keycloakRealm")
    logger.info(s"Player Service URL: $playerServiceUrl")
    
    // Create ActorSystem
    implicit val system: ActorSystem[?] = ActorSystem(Behaviors.empty, "auth-service")
    implicit val ec: ExecutionContextExecutor = system.executionContext
    
    // Create clients
    val keycloakClient = new KeycloakClient(
      keycloakUrl = keycloakUrl,
      realm = keycloakRealm,
      clientId = keycloakClientId,
      clientSecret = keycloakClientSecret,
      adminUsername = keycloakAdminUsername,
      adminPassword = keycloakAdminPassword
    )
    
    val playerServiceClient = new PlayerServiceClient(playerServiceUrl)
    
    // Create routes
    val authRoutes = new AuthRoutes(keycloakClient, playerServiceClient)
    
    // Start HTTP server
    val bindingFuture = Http()
      .newServerAt("0.0.0.0", servicePort)
      .bind(authRoutes.routes)
    
    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"Auth Service online at http://${address.getHostString}:${address.getPort}")
      case Failure(exception) =>
        logger.error(s"Failed to bind to port $servicePort", exception)
        system.terminate()
    }
    
    // Add shutdown hook
    sys.addShutdownHook {
      logger.info("Shutting down Auth Service...")
      bindingFuture.foreach(_.unbind())
      keycloakClient.close()
      playerServiceClient.close()
      system.terminate()
    }
  }
}
