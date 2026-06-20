package player

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection}
import com.mongodb.client.model.{Filters, Sorts, Updates, IndexOptions}
import org.bson.Document

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters._

/** MongoDB-backed player repository. Persists player data across restarts. */
class MongoPlayerRepository(client: MongoClient, databaseName: String) extends PlayerRepository {

  private val database   = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("players")

  // Clean up any legacy documents without a 'username' field (from old schema collision)
  val deleted = collection.deleteMany(Filters.exists("username", false))
  if (deleted.getDeletedCount > 0) {
    println(s"[INFO] Cleaned up ${deleted.getDeletedCount} legacy documents without username field")
  }

  // Ensure unique index on username
  try {
    collection.createIndex(new Document("username", 1), new IndexOptions().unique(true))
  } catch {
    case _: Exception =>
      // Drop the problematic index and recreate
      try { collection.dropIndex("username_1") } catch { case _: Exception => () }
      collection.createIndex(new Document("username", 1), new IndexOptions().unique(true))
  }

  override def create(req: CreatePlayerRequest): Player = {
    val id  = UUID.randomUUID().toString
    val now = Instant.now().toString
    val player = Player(
      id          = id,
      username    = req.username,
      email       = req.email,
      rating      = req.initialRating,
      gamesPlayed = 0,
      wins        = 0,
      losses      = 0,
      draws       = 0,
      createdAt   = now,
      lastSeenAt  = now
    )
    collection.insertOne(toDocument(player))
    player
  }

  override def findById(id: String): Option[Player] =
    Option(collection.find(Filters.eq("id", id)).first()).map(fromDocument)

  override def findByUsername(username: String): Option[Player] =
    Option(collection.find(Filters.eq("username", username)).first()).map(fromDocument)

  override def findAll(): List[Player] =
    collection.find().sort(Sorts.ascending("username")).asScala.map(fromDocument).toList

  override def update(id: String, req: UpdatePlayerRequest): Option[Player] = {
    val existing = findById(id)
    existing.map { p =>
      val updates = new java.util.ArrayList[org.bson.conversions.Bson]()
      req.email.foreach(e  => updates.add(Updates.set("email", e)))
      req.rating.foreach(r => updates.add(Updates.set("rating", r)))
      updates.add(Updates.set("lastSeenAt", Instant.now().toString))

      if (!updates.isEmpty) {
        collection.updateOne(Filters.eq("id", id), Updates.combine(updates))
      }
      findById(id).getOrElse(p)
    }
  }

  override def recordGame(req: RecordGameRequest): Option[Player] = {
    val existing = findById(req.playerId)
    existing.map { p =>
      val (winsInc, lossesInc, drawsInc) = req.result.toLowerCase match {
        case "win"  => (1, 0, 0)
        case "loss" => (0, 1, 0)
        case "draw" => (0, 0, 1)
        case _      => return Some(p) // Invalid result: no changes
      }

      collection.updateOne(
        Filters.eq("id", req.playerId),
        Updates.combine(
          Updates.set("rating", math.max(0, p.rating + req.ratingChange)),
          Updates.inc("gamesPlayed", 1),
          Updates.inc("wins", winsInc),
          Updates.inc("losses", lossesInc),
          Updates.inc("draws", drawsInc),
          Updates.set("lastSeenAt", Instant.now().toString)
        )
      )
      findById(req.playerId).getOrElse(p)
    }
  }

  override def delete(id: String): Boolean =
    collection.deleteOne(Filters.eq("id", id)).getDeletedCount > 0

  override def leaderboard(limit: Int = 20): List[PlayerStats] =
    collection.find()
      .sort(Sorts.descending("rating"))
      .limit(limit)
      .asScala
      .map(fromDocument)
      .toList
      .zipWithIndex
      .map { case (p, idx) =>
        PlayerStats(
          playerId    = p.id,
          username    = p.username,
          rating      = p.rating,
          gamesPlayed = p.gamesPlayed,
          wins        = p.wins,
          losses      = p.losses,
          draws       = p.draws,
          winRate     = p.winRate,
          rank        = idx + 1
        )
      }

  // ── Document conversion helpers ──────────────────────────────────────────

  private def toDocument(p: Player): Document = {
    val doc = new Document()
      .append("id", p.id)
      .append("username", p.username)
      .append("rating", p.rating)
      .append("gamesPlayed", p.gamesPlayed)
      .append("wins", p.wins)
      .append("losses", p.losses)
      .append("draws", p.draws)
      .append("createdAt", p.createdAt)
      .append("lastSeenAt", p.lastSeenAt)
    p.email.foreach(e => doc.append("email", e))
    doc
  }

  private def fromDocument(doc: Document): Player = Player(
    id          = doc.getString("id"),
    username    = doc.getString("username"),
    email       = Option(doc.getString("email")),
    rating      = doc.getInteger("rating", 1200),
    gamesPlayed = doc.getInteger("gamesPlayed", 0),
    wins        = doc.getInteger("wins", 0),
    losses      = doc.getInteger("losses", 0),
    draws       = doc.getInteger("draws", 0),
    createdAt   = doc.getString("createdAt"),
    lastSeenAt  = doc.getString("lastSeenAt")
  )
}

object MongoPlayerRepository {
  /** Create a MongoPlayerRepository from environment variables. */
  def fromEnv(): MongoPlayerRepository = {
    val host     = sys.env.getOrElse("MONGODB_HOST", "localhost")
    val port     = sys.env.getOrElse("MONGODB_PORT", "27017")
    val database = sys.env.getOrElse("MONGODB_DATABASE", "chess")
    val uri      = s"mongodb://$host:$port"
    val client   = MongoClients.create(uri)
    // Eagerly verify the connection works
    val db = client.getDatabase(database)
    db.runCommand(new Document("ping", 1))
    val repo = new MongoPlayerRepository(client, database)
    val count = repo.findAll().size
    println(s"[INFO] MongoDB player repository ready — $count existing players loaded")
    repo
  }
}
