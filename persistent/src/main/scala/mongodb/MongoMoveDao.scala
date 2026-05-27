package mongodb

import dao.MoveDao
import _root_.model.{Move, Position}
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts.ascending
import org.bson.Document
import com.mongodb.client.MongoClient

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/** MongoDB implementation of MoveDao using Java driver. */
class MongoMoveDao(client: MongoClient, databaseName: String)(implicit ec: ExecutionContext) extends MoveDao {

  private val database = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("moves")

  override def create(move: Move, gameId: String, moveIndex: Int): Future[Int] = {
    Future {
      val doc = new Document()
        .append("gameId", gameId)
        .append("moveIndex", moveIndex)
        .append("fromCol", move.from.col)
        .append("fromRow", move.from.row)
        .append("toCol", move.to.col)
        .append("toRow", move.to.row)
      collection.insertOne(doc)
      Math.abs((gameId + moveIndex).hashCode)
    }
  }

  override def findById(id: Int): Future[Option[Move]] = {
    Future.successful(None)
  }

  override def findByGameId(gameId: String): Future[List[Move]] = {
    Future {
      collection
        .find(Filters.eq("gameId", gameId))
        .sort(ascending("moveIndex"))
        .asScala
        .map { doc =>
          Move(
            Position(doc.getInteger("fromCol"), doc.getInteger("fromRow")),
            Position(doc.getInteger("toCol"), doc.getInteger("toRow"))
          )
        }.toList
    }
  }

  override def deleteByGameId(gameId: String): Future[Int] = {
    Future {
      collection.deleteMany(Filters.eq("gameId", gameId)).getDeletedCount.toInt
    }
  }
}
