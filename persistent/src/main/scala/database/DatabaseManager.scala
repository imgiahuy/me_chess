package database

import dao.{GameDao, MoveDao, PlayerDao}
import mongodb.{MongoGameDao, MongoMoveDao, MongoPlayerDao}
import com.mongodb.client.MongoClient as JavaMongoClient
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.ServerAddress
import slick.{H2Tables, PostgresTables, SlickGameDao, SlickMoveDao, SlickPlayerDao, Tables}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.JdbcProfile

/** Manages database connections and provides DAO instances. */
class DatabaseManager(db: Database, tables: Tables)(implicit ec: ExecutionContext) {
  import tables.profile.api._

  private val _playerDao = new SlickPlayerDao(db, tables)
  private val _moveDao = new SlickMoveDao(db, tables)
  private val _gameDao = new SlickGameDao(db, tables, _playerDao, _moveDao)

  /** Initializes the database schema. */
  def initializeSchema(): Future[Unit] = {
    db.run(tables.schema.create)
  }

  /** Drops all tables and recreates the schema. */
  def resetSchema(): Future[Unit] = {
    db.run(tables.schema.drop).flatMap(_ => initializeSchema())
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
}

/** MongoDB database manager. */
class MongoDatabaseManager(
  client: JavaMongoClient,
  databaseName: String
)(implicit ec: ExecutionContext) {

  private val _playerDao = new MongoPlayerDao(client, databaseName)
  private val _moveDao = new MongoMoveDao(client, databaseName)
  private val _gameDao = new MongoGameDao(client, databaseName, _playerDao, _moveDao)

  /** MongoDB doesn't require schema initialization - collections are created on demand. */
  def initializeSchema(): Future[Unit] = {
    Future.successful(())
  }

  /** Drops all collections. */
  def resetSchema(): Future[Unit] = {
    Future {
      val db = client.getDatabase(databaseName)
      db.getCollection("games").drop()
      db.getCollection("players").drop()
      db.getCollection("moves").drop()
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

  /** Creates a DatabaseManager with a PostgreSQL database. */
  def postgresql(
    host: String = "localhost",
    port: Int = 5432,
    database: String,
    user: String,
    password: String
  )(implicit ec: ExecutionContext): DatabaseManager = {
    val url = s"jdbc:postgresql://$host:$port/$database"
    val db = Database.forURL(
      url = url,
      driver = "org.postgresql.Driver",
      user = user,
      password = password
    )
    new DatabaseManager(db, PostgresTables)
  }

  /** Creates a DatabaseManager with a MongoDB database. */
  def mongodb(
    host: String = "localhost",
    port: Int = 27017,
    database: String = "chess"
  )(implicit ec: ExecutionContext): MongoDatabaseManager = {
    val settings = MongoClientSettings.builder()
      .applyToClusterSettings(builder => builder.hosts(java.util.List.of(new ServerAddress(host, port))))
      .build()
    val client = MongoClients.create(settings)
    new MongoDatabaseManager(client, database)
  }

  /** Creates a DatabaseManager with a custom Database instance and Tables. */
  def fromDatabase(db: Database, tables: Tables)(implicit ec: ExecutionContext): DatabaseManager = {
    new DatabaseManager(db, tables)
  }
}
