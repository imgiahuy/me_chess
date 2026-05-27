package mongodb

import dao.GameDao
import _root_.model.{Board, Black, Bishop, Color, King, Knight, Move, Pawn, Piece, PieceType, Player, Position, PositionState, Queen, Rook, White}
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates.set
import org.bson.Document
import com.mongodb.client.MongoClient

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/** MongoDB implementation of GameDao using Java driver. */
class MongoGameDao(
  client: MongoClient,
  databaseName: String,
  playerDao: MongoPlayerDao,
  moveDao: MongoMoveDao
)(implicit ec: ExecutionContext) extends GameDao {

  private val database = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("games")

  override def create(game: PositionState): Future[String] = {
    (for {
      whiteId <- playerDao.getOrCreateId(game.whitePlayer.name)
      blackId <- playerDao.getOrCreateId(game.blackPlayer.name)
    } yield {
      val gameId = java.util.UUID.randomUUID().toString
      val boardJson = serializeBoard(game.board)
      
      val doc = new Document()
        .append("id", gameId)
        .append("whitePlayerId", whiteId)
        .append("blackPlayerId", blackId)
        .append("turn", game.turn.toString)
        .append("creationDate", java.util.Date.from(game.creationDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant))
        .append("lastModified", new java.util.Date())
        .append("boardState", boardJson)
      
      collection.insertOne(doc)
      gameId
    }).flatMap { gameId =>
      val moveFutures = game.moveHistory.zipWithIndex.map { case (move, index) =>
        moveDao.create(move, gameId, index)
      }
      Future.sequence(moveFutures).map(_ => gameId)
    }
  }

  override def findById(id: String): Future[Option[PositionState]] = {
    Future {
      val doc = collection.find(Filters.eq("id", id)).first()
      if (doc != null) {
        val whitePlayerId = doc.getInteger("whitePlayerId")
        val blackPlayerId = doc.getInteger("blackPlayerId")
        
        // Note: This is a simplification - in production you'd store actual player names
        val whitePlayer = Player(s"Player$whitePlayerId")
        val blackPlayer = Player(s"Player$blackPlayerId")
        
        val board = deserializeBoard(doc.getString("boardState"))
        val turn = parseColor(doc.getString("turn"))
        val creationDate = doc.getDate("creationDate").toInstant
          .atZone(java.time.ZoneId.systemDefault()).toLocalDate
        
        // Get moves
        val moves = scala.concurrent.Await.result(moveDao.findByGameId(id), scala.concurrent.duration.Duration(5, "seconds"))
        
        Some(PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id)))
      } else {
        None
      }
    }
  }

  override def update(id: String, game: PositionState): Future[Boolean] = {
    Future {
      val boardJson = serializeBoard(game.board)
      val result = collection.updateOne(
        Filters.eq("id", id),
        new Document("$set", new Document()
          .append("turn", game.turn.toString)
          .append("boardState", boardJson)
          .append("lastModified", new java.util.Date()))
      )
      
      if (result.getModifiedCount > 0) {
        // Update moves: delete old ones and insert new ones
        scala.concurrent.Await.result(moveDao.deleteByGameId(id), scala.concurrent.duration.Duration(5, "seconds"))
        val moveFutures = game.moveHistory.zipWithIndex.map { case (move, index) =>
          moveDao.create(move, id, index)
        }
        scala.concurrent.Await.result(Future.sequence(moveFutures), scala.concurrent.duration.Duration(5, "seconds"))
        true
      } else {
        false
      }
    }
  }

  override def delete(id: String): Future[Boolean] = {
    Future {
      scala.concurrent.Await.result(moveDao.deleteByGameId(id), scala.concurrent.duration.Duration(5, "seconds"))
      collection.deleteOne(Filters.eq("id", id)).getDeletedCount > 0
    }
  }

  override def listAll(): Future[List[String]] = {
    Future {
      collection.find().asScala.map(_.getString("id")).toList
    }
  }

  override def findAll(): Future[List[PositionState]] = {
    Future {
      collection.find().asScala.map { doc =>
        val gameId = doc.getString("id")
        val whitePlayerId = doc.getInteger("whitePlayerId")
        val blackPlayerId = doc.getInteger("blackPlayerId")
        
        val whitePlayer = Player(s"Player$whitePlayerId")
        val blackPlayer = Player(s"Player$blackPlayerId")
        
        val board = deserializeBoard(doc.getString("boardState"))
        val turn = parseColor(doc.getString("turn"))
        val creationDate = doc.getDate("creationDate").toInstant
          .atZone(java.time.ZoneId.systemDefault()).toLocalDate
        
        val moves = scala.concurrent.Await.result(moveDao.findByGameId(gameId), scala.concurrent.duration.Duration(5, "seconds"))
        
        PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(gameId))
      }.toList
    }
  }

  override def findLatest(): Future[Option[PositionState]] = {
    Future {
      val doc = collection.find().sort(new Document("lastModified", -1)).first()
      if (doc != null) {
        val gameId = doc.getString("id")
        val whitePlayerId = doc.getInteger("whitePlayerId")
        val blackPlayerId = doc.getInteger("blackPlayerId")
        
        val whitePlayer = Player(s"Player$whitePlayerId")
        val blackPlayer = Player(s"Player$blackPlayerId")
        
        val board = deserializeBoard(doc.getString("boardState"))
        val turn = parseColor(doc.getString("turn"))
        val creationDate = doc.getDate("creationDate").toInstant
          .atZone(java.time.ZoneId.systemDefault()).toLocalDate
        
        val moves = scala.concurrent.Await.result(moveDao.findByGameId(gameId), scala.concurrent.duration.Duration(5, "seconds"))
        
        Some(PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(gameId)))
      } else {
        None
      }
    }
  }

  // Helper methods for serialization/deserialization

  private def serializeBoard(board: Board): String = {
    val squaresData = board.squares.map { case (pos, piece) =>
      s"${pos.col},${pos.row},${piece.color},${piece.pieceType}"
    }.mkString(";")
    squaresData
  }

  private def deserializeBoard(json: String): Board = {
    val squares = if (json.isEmpty) {
      Map.empty[Position, Piece]
    } else {
      json.split(";").map { entry =>
        val parts = entry.split(",")
        val pos = Position(parts(0).toInt, parts(1).toInt)
        val color = parseColor(parts(2))
        val pieceType = parsePieceType(parts(3))
        pos -> Piece(color, pieceType)
      }.toMap
    }
    Board(squares)
  }

  private def parseColor(str: String): Color = str match {
    case "White" => White
    case "Black" => Black
    case _ => throw new IllegalArgumentException(s"Invalid color: $str")
  }

  private def parsePieceType(str: String): PieceType = str match {
    case "King" => King
    case "Queen" => Queen
    case "Rook" => Rook
    case "Bishop" => Bishop
    case "Knight" => Knight
    case "Pawn" => Pawn
    case _ => throw new IllegalArgumentException(s"Invalid piece type: $str")
  }
}
