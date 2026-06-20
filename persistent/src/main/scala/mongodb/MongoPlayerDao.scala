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

  override def create(player: Player): Future[Int] = {
    Future {
      val doc = new Document("name", player.name)
      collection.insertOne(doc)
      Math.abs(player.name.hashCode)
    }
  }

  override def findById(id: Int): Future[Option[Player]] = {
    Future.successful(None) // Not easily supported with current schema
  }

  override def findByName(name: String): Future[Option[Player]] = {
    Future {
      val doc = collection.find(Filters.eq("name", name)).first()
      if (doc != null) Some(Player(name)) else None
    }
  }

  override def update(id: Int, player: Player): Future[Boolean] = {
    Future {
      val result = collection.updateOne(Filters.eq("name", player.name), set("name", player.name))
      result.getModifiedCount > 0
    }
  }

  override def delete(id: Int): Future[Boolean] = {
    Future.successful(false)
  }

  override def findAll(): Future[List[Player]] = {
    Future {
      collection.find().asScala.map(doc => Player(doc.getString("name"))).toList
    }
  }

  override def getOrCreateId(name: String): Future[Int] = {
    findByName(name).flatMap {
      case Some(_) => Future.successful(Math.abs(name.hashCode))
      case None => create(Player(name))
    }
  }
}
