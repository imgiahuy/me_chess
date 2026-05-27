package database

import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

/** Database configuration and connection management. */
object DatabaseConfig {

  /** Creates a H2 in-memory database for testing. */
  def createH2InMemory()(implicit ec: ExecutionContext): Database = {
    Database.forConfig("h2mem")
  }

  /** Creates a H2 file-based database. */
  def createH2FileBased(path: String)(implicit ec: ExecutionContext): Database = {
    val jdbcUrl = s"jdbc:h2:$path;DB_CLOSE_DELAY=-1"
    Database.forURL(jdbcUrl, driver = "org.h2.Driver")
  }

  /** Creates a PostgreSQL database. */
  def createPostgreSQL(
    host: String = "localhost",
    port: Int = 5432,
    database: String,
    user: String,
    password: String
  )(implicit ec: ExecutionContext): Database = {
    val url = s"jdbc:postgresql://$host:$port/$database"
    Database.forURL(
      url = url,
      driver = "org.postgresql.Driver",
      user = user,
      password = password
    )
  }

  /** Creates a database from application.conf configuration. */
  def createFromConfig(configPath: String = "slick.db")(implicit ec: ExecutionContext): Database = {
    Database.forConfig(configPath)
  }
}
