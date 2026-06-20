package mongodb

import dao.MoveDao
import _root_.model.{Bishop, CastlingKingSide, CastlingQueenSide, EnPassant, Knight, Move, Position, Promotion, Queen, Rook, SpecialMoveType}
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
        .append("specialMove", serializeSpecialMove(move.specialMove))
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
            Position(doc.getInteger("toCol"), doc.getInteger("toRow")),
            deserializeSpecialMove(doc.getString("specialMove"))
          )
        }.toList
    }
  }

  private def serializeSpecialMove(sm: Option[SpecialMoveType]): String = sm match {
    case None => null
    case Some(CastlingKingSide)  => "castling_kingside"
    case Some(CastlingQueenSide) => "castling_queenside"
    case Some(EnPassant)         => "en_passant"
    case Some(Promotion(Queen))  => "promotion_queen"
    case Some(Promotion(Rook))   => "promotion_rook"
    case Some(Promotion(Bishop)) => "promotion_bishop"
    case Some(Promotion(Knight)) => "promotion_knight"
    case Some(Promotion(_))      => "promotion_queen"
  }

  private def deserializeSpecialMove(s: String): Option[SpecialMoveType] = s match {
    case null | ""              => None
    case "castling_kingside"    => Some(CastlingKingSide)
    case "castling_queenside"   => Some(CastlingQueenSide)
    case "en_passant"           => Some(EnPassant)
    case "promotion_queen"      => Some(Promotion(Queen))
    case "promotion_rook"       => Some(Promotion(Rook))
    case "promotion_bishop"     => Some(Promotion(Bishop))
    case "promotion_knight"     => Some(Promotion(Knight))
    case _                      => None
  }

  override def deleteByGameId(gameId: String): Future[Int] = {
    Future {
      collection.deleteMany(Filters.eq("gameId", gameId)).getDeletedCount.toInt
    }
  }
}
