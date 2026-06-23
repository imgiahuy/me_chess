package mongodb

import dao.PlayerDao
import _root_.model.Player
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates.set
import org.bson.Document
import com.mongodb.client.MongoClient

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/** MongoDB implementation of PlayerDao using Java driver. */
class MongoPlayerDao(client: MongoClient, databaseName: String)(implicit ec: ExecutionContext) extends PlayerDao {

  private val database = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("game_players")

  private val idCounter = new java.util.concurrent.atomic.AtomicInteger(
    // Initialize counter from existing documents
    Option(collection.find().sort(new Document("playerId", -1)).first())
      .flatMap(doc => Option(doc.getInteger("playerId")).map(_.toInt))
      .getOrElse(0)
  )

  override def create(player: Player): Future[Int] = {
    Future {
      val newId = idCounter.incrementAndGet()
      val doc = new Document("name", player.name)
        .append("playerId", newId)
        .append("elo", 1200)
      collection.insertOne(doc)
      newId
    }
  }

  override def findById(id: Int): Future[Option[Player]] = {
    Future {
      val doc = collection.find(Filters.eq("playerId", id)).first()
      if (doc != null) Some(Player(doc.getString("name"))) else None
    }
  }

  override def findByName(name: String): Future[Option[Player]] = {
    Future {
      val doc = collection.find(Filters.eq("name", name)).first()
      if (doc != null) Some(Player(name)) else None
    }
  }

  override def update(id: Int, player: Player): Future[Boolean] = {
    Future {
      val result = collection.updateOne(
        Filters.eq("playerId", id),
        new Document("$set", new Document("name", player.name))
      )
      result.getModifiedCount > 0
    }
  }

  override def delete(id: Int): Future[Boolean] = {
    Future {
      val result = collection.deleteOne(Filters.eq("playerId", id))
      result.getDeletedCount > 0
    }
  }

  override def findAll(): Future[List[Player]] = {
    Future {
      collection.find().asScala.map(doc => Player(doc.getString("name"))).toList
    }
  }

  override def getOrCreateId(name: String): Future[Int] = {
    Future {
      val doc = collection.find(Filters.eq("name", name)).first()
      if (doc != null) {
        Option(doc.getInteger("playerId")).map(_.toInt).getOrElse {
          // Legacy document without playerId - upgrade it
          val newId = idCounter.incrementAndGet()
          collection.updateOne(Filters.eq("name", name), new Document("$set", new Document("playerId", newId)))
          newId
        }
      } else {
        val newId = idCounter.incrementAndGet()
        val newDoc = new Document("name", name)
          .append("playerId", newId)
          .append("elo", 1200)
        collection.insertOne(newDoc)
        newId
      }
    }
  }

  override def updateElo(id: Int, newElo: Int): Future[Boolean] = {
    Future {
      val result = collection.updateOne(
        Filters.eq("playerId", id),
        new Document("$set", new Document("elo", newElo))
      )
      result.getModifiedCount > 0
    }
  }

  override def findLeaderboard(): Future[List[(Player, Int)]] = {
    Future {
      collection.find()
        .sort(new Document("elo", -1))
        .asScala
        .map { doc =>
          val name = doc.getString("name")
          val elo = Option(doc.getInteger("elo")).map(_.toInt).getOrElse(1200)
          (Player(name), elo)
        }.toList
    }
  }
}
