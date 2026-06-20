package database

import dao.{GameDao, GameEventDao, MoveDao, PlayerDao}
import mongodb.{MongoGameDao, MongoGameEventDao, MongoMoveDao, MongoPlayerDao}
import com.mongodb.client.MongoClient as JavaMongoClient
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.ServerAddress
import slick.{H2Tables, PostgresTables, SlickGameDao, SlickGameEventDao, SlickMoveDao, SlickPlayerDao, Tables}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Manages database connections and provides DAO instances. */
class DatabaseManager(
  db: Database,
  tables: Tables,
  flywayCredentials: Option[(String, String, String)] = None,
  redisPool: Option[redis.clients.jedis.JedisPool] = None
)(implicit ec: ExecutionContext) {
  import tables.profile.api._

  private val _rawPlayerDao = new SlickPlayerDao(db, tables)
  private val _playerDao: PlayerDao = redisPool match {
    case Some(pool) => new redis.CachedPlayerDao(_rawPlayerDao, pool)
    case None => _rawPlayerDao
  }
  private val _moveDao = new SlickMoveDao(db, tables)
  private val _rawGameDao = new SlickGameDao(db, tables, _rawPlayerDao, _moveDao)
  private val _gameDao: GameDao = redisPool match {
    case Some(pool) => new redis.CachedGameDao(_rawGameDao, pool)
    case None => _rawGameDao
  }

  /** Initializes the database schema using Slick DDL (for H2 in-memory tests only). */
  def initializeSchema(): Future[Unit] = {
    db.run(tables.schema.create)
  }

  /** Initializes the database schema using Flyway versioned migrations.
    *
    * This is the preferred approach for production PostgreSQL and H2 file-based databases.
    */
  def initializeSchemaWithMigrations(jdbcUrl: String, user: String, password: String): Future[Unit] = {
    Future {
      FlywayMigrationRunner.migrate(jdbcUrl, user, password)
    }
  }

  /** Resets the schema.
    *
    * For Flyway-managed databases (Postgres) this runs Flyway clean + migrate so the
    * schema is always consistent with the versioned migration scripts.
    * For H2 in-memory databases this falls back to Slick DDL drop + create.
    */
  def resetSchema(): Future[Unit] = {
    flywayCredentials match {
      case Some((url, user, password)) =>
        Future { FlywayMigrationRunner.cleanAndMigrate(url, user, password) }.map(_ => ())
      case None =>
        db.run(tables.schema.dropIfExists).flatMap(_ => initializeSchema())
    }
  }

  /** Closes the database connection. */
  def close(): Future[Unit] = {
    db.shutdown
    Future.successful(())
  }

  /** Returns the PlayerDao instance. */
  def playerDao: PlayerDao = _playerDao

  /** Returns the MoveDao instance. */
  def moveDao: MoveDao = _moveDao

  /** Returns the GameDao instance. */
  def gameDao: GameDao = _gameDao

  /** Returns the GameEventDao for the transactional outbox pattern. */
  lazy val gameEventDao: GameEventDao = new SlickGameEventDao(db, tables)
}

/** MongoDB database manager. */
class MongoDatabaseManager(
  client: JavaMongoClient,
  databaseName: String,
  redisPool: Option[redis.clients.jedis.JedisPool] = None
)(implicit ec: ExecutionContext) {

  private val _rawPlayerDao = new MongoPlayerDao(client, databaseName)
  private val _playerDao: PlayerDao = redisPool match {
    case Some(pool) => new redis.CachedPlayerDao(_rawPlayerDao, pool)
    case None => _rawPlayerDao
  }
  private val _moveDao = new MongoMoveDao(client, databaseName)
  private val _rawGameDao = new MongoGameDao(client, databaseName, _playerDao, _moveDao)
  private val _gameDao: GameDao = redisPool match {
    case Some(pool) => new redis.CachedGameDao(_rawGameDao, pool)
    case None => _rawGameDao
  }

  /** MongoDB doesn't require schema initialization - collections are created on demand. */
  def initializeSchema(): Future[Unit] = {
    Future.successful(())
  }

  /** Drops all collections. */
  def resetSchema(): Future[Unit] = {
    Future {
      val db = client.getDatabase(databaseName)
      db.getCollection("games").drop()
      db.getCollection("game_players").drop()
      db.getCollection("moves").drop()
      db.getCollection("game_events").drop()
    }
  }

  /** Closes the database connection. */
  def close(): Future[Unit] = {
    Future {
      client.close()
    }
  }

  /** Returns the PlayerDao instance. */
  def playerDao: PlayerDao = _playerDao

  /** Returns the MoveDao instance. */
  def moveDao: MoveDao = _moveDao

  /** Returns the GameDao instance. */
  def gameDao: GameDao = _gameDao

  /** Returns the GameEventDao for the transactional outbox pattern. */
  lazy val gameEventDao: GameEventDao = new MongoGameEventDao(client, databaseName)
}

/** Factory for creating DatabaseManager instances. */
object DatabaseManager {

  /** Creates a DatabaseManager with an in-memory H2 database. */
  def h2InMemory()(implicit ec: ExecutionContext): DatabaseManager = {
    val db = Database.forURL(
      url = "jdbc:h2:mem:chess;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    new DatabaseManager(db, H2Tables)
  }

  /** Creates a DatabaseManager with a file-based H2 database. */
  def h2FileBased(path: String)(implicit ec: ExecutionContext): DatabaseManager = {
    val db = Database.forURL(
      url = s"jdbc:h2:$path;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    new DatabaseManager(db, H2Tables)
  }

  /** Creates a DatabaseManager for PostgreSQL from a full JDBC URL.
    *
    * Useful in tests where the JDBC URL is provided directly (e.g. Testcontainers).
    */
  def postgresqlFromUrl(
    jdbcUrl: String,
    user: String,
    password: String,
    maxConnections: Int = 20,
    redisPool: Option[redis.clients.jedis.JedisPool] = None
  )(implicit ec: ExecutionContext): DatabaseManager = {
    val db = Database.forURL(
      url = jdbcUrl,
      driver = "org.postgresql.Driver",
      user = user,
      password = password,
      executor = slick.util.AsyncExecutor("postgresql", numThreads = maxConnections, queueSize = 1000)
    )
    new DatabaseManager(db, PostgresTables, flywayCredentials = Some((jdbcUrl, user, password)), redisPool = redisPool)
  }

  /** Creates a DatabaseManager with a PostgreSQL database backed by HikariCP. */
  def postgresql(
    host: String = "localhost",
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    maxConnections: Int = 20,
    redisPool: Option[redis.clients.jedis.JedisPool] = None
  )(implicit ec: ExecutionContext): DatabaseManager = {
    val url = s"jdbc:postgresql://$host:$port/$database"
    val db = Database.forURL(
      url = url,
      driver = "org.postgresql.Driver",
      user = user,
      password = password,
      executor = slick.util.AsyncExecutor("postgresql", numThreads = maxConnections, queueSize = 1000)
    )
    new DatabaseManager(db, PostgresTables, flywayCredentials = Some((url, user, password)), redisPool = redisPool)
  }

  /** Creates a DatabaseManager with a MongoDB database. */
  def mongodb(
    host: String = "localhost",
    port: Int = 27017,
    database: String = "chess",
    redisPool: Option[redis.clients.jedis.JedisPool] = None
  )(implicit ec: ExecutionContext): MongoDatabaseManager = {
    val settings = MongoClientSettings.builder()
      .applyToClusterSettings(builder => builder.hosts(java.util.List.of(new ServerAddress(host, port))))
      .build()
    val client = MongoClients.create(settings)
    new MongoDatabaseManager(client, database, redisPool)
  }

  /** Creates a DatabaseManager with a custom Database instance and Tables. */
  def fromDatabase(db: Database, tables: Tables)(implicit ec: ExecutionContext): DatabaseManager = {
    new DatabaseManager(db, tables)
  }
}
