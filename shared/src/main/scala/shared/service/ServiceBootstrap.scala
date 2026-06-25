package shared.service

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.mongodb.client.MongoClients
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}

/** Common service bootstrap utilities for microservices. */
object ServiceBootstrap {

  /** Creates an ActorSystem with the given name. */
  def createActorSystem(systemName: String): ActorSystem[Unit] =
    ActorSystem(Behaviors.empty[Unit], systemName)

  /** Attempts to connect to MongoDB with retry logic.
   * 
   * @param maxRetries Maximum number of connection attempts
   * @return Success(MongoClient) if connection succeeds, Failure otherwise
   */
  def connectToMongoDBWithRetry(maxRetries: Int = 5): Try[com.mongodb.client.MongoClient] = {
    val mongoHost = sys.env.getOrElse("MONGODB_HOST", "localhost")
    val mongoPort = sys.env.getOrElse("MONGODB_PORT", "27017").toInt
    val mongoUser = sys.env.getOrElse("MONGODB_USER", "")
    val mongoPassword = sys.env.getOrElse("MONGODB_PASSWORD", "")
    val mongoAuthDb = sys.env.getOrElse("MONGODB_AUTH_DB", "admin")

    var attempt = 0
    var lastError: Option[Throwable] = None

    while (attempt < maxRetries) {
      attempt += 1
      try {
        val settings = if (mongoUser.nonEmpty && mongoPassword.nonEmpty) {
          val credential = MongoCredential.createCredential(
            mongoUser,
            mongoAuthDb,
            mongoPassword.toCharArray
          )
          MongoClientSettings.builder()
            .applyToClusterSettings { builder =>
              builder.hosts(List(new ServerAddress(mongoHost, mongoPort)).asJava)
            }
            .credential(credential)
            .build()
        } else {
          MongoClientSettings.builder()
            .applyToClusterSettings { builder =>
              builder.hosts(List(new ServerAddress(mongoHost, mongoPort)).asJava)
            }
            .build()
        }

        val client = MongoClients.create(settings)
        // Test connection
        client.getDatabase("admin").runCommand(org.bson.BsonDocument.parse("{ ping: 1 }"))
        println(s"[INFO] Connected to MongoDB at $mongoHost:$mongoPort")
        return Success(client)
      } catch {
        case e: Exception =>
          lastError = Some(e)
          println(s"[WARN] MongoDB connection attempt $attempt/$maxRetries failed: ${e.getMessage}")
          if (attempt < maxRetries) {
            println(s"[INFO] Retrying in ${attempt * 2} seconds...")
            Thread.sleep(attempt * 2000L)
          }
      }
    }

    lastError match {
      case Some(error) => Failure(error)
      case None => Failure(new Exception("MongoDB connection failed: unknown error"))
    }
  }

  /** Checks if MongoDB is enabled via environment variables. */
  def isMongoDBEnabled: Boolean = sys.env.contains("MONGODB_HOST")
}
