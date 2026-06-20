package database

import redis.clients.jedis.JedisPool
import com.mongodb.client.MongoClient
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Health check result for a database component. */
case class HealthStatus(
  component: String,
  healthy: Boolean,
  latencyMs: Long = 0,
  details: Option[String] = None
)

/** Provides health check capabilities for all persistence components.
  *
  * Checks connectivity and basic responsiveness of:
  * - PostgreSQL (via Slick)
  * - MongoDB
  * - Redis
  */
object DatabaseHealthCheck {

  /** Checks Redis health by issuing a PING command. */
  def checkRedis(pool: JedisPool)(implicit ec: ExecutionContext): Future[HealthStatus] = {
    Future {
      val start = System.currentTimeMillis()
      try {
        val jedis = pool.getResource
        try {
          val response = jedis.ping()
          val latency = System.currentTimeMillis() - start
          HealthStatus("redis", response == "PONG", latency)
        } finally {
          jedis.close()
        }
      } catch {
        case e: Exception =>
          val latency = System.currentTimeMillis() - start
          HealthStatus("redis", healthy = false, latency, Some(e.getMessage))
      }
    }
  }

  /** Checks MongoDB health by issuing an admin ping command. */
  def checkMongoDB(client: MongoClient, databaseName: String)(implicit ec: ExecutionContext): Future[HealthStatus] = {
    Future {
      val start = System.currentTimeMillis()
      try {
        val db = client.getDatabase(databaseName)
        val result = db.runCommand(new org.bson.Document("ping", 1))
        val latency = System.currentTimeMillis() - start
        val ok = Option(result.getDouble("ok")).exists(_ == 1.0)
        HealthStatus("mongodb", ok, latency)
      } catch {
        case e: Exception =>
          val latency = System.currentTimeMillis() - start
          HealthStatus("mongodb", healthy = false, latency, Some(e.getMessage))
      }
    }
  }

  /** Checks PostgreSQL health by running a trivial query. */
  def checkPostgreSQL(db: Database)(implicit ec: ExecutionContext): Future[HealthStatus] = {
    import slick.jdbc.PostgresProfile.api._
    val start = System.currentTimeMillis()
    db.run(sql"SELECT 1".as[Int].head).map { _ =>
      val latency = System.currentTimeMillis() - start
      HealthStatus("postgresql", healthy = true, latency)
    }.recover { case e: Exception =>
      val latency = System.currentTimeMillis() - start
      HealthStatus("postgresql", healthy = false, latency, Some(e.getMessage))
    }
  }

  /** Aggregates all health checks into a single composite result. */
  def checkAll(
    redisPool: Option[JedisPool] = None,
    mongoClient: Option[(MongoClient, String)] = None,
    slickDb: Option[Database] = None
  )(implicit ec: ExecutionContext): Future[List[HealthStatus]] = {
    val checks = List(
      redisPool.map(checkRedis),
      mongoClient.map { case (client, dbName) => checkMongoDB(client, dbName) },
      slickDb.map(checkPostgreSQL)
    ).flatten

    Future.sequence(checks)
  }

  /** Returns true if all components are healthy. */
  def isHealthy(statuses: List[HealthStatus]): Boolean = statuses.forall(_.healthy)
}
