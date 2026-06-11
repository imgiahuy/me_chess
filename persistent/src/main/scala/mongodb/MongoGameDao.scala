package mongodb

import dao.GameDao
import _root_.model.{Board, Black, Bishop, Checkmate, Color, DeadPosition, Draw, DrawReason, FiftyMoveRule, InsufficientMaterial, King, Knight, Move, MutualAgreement, Ongoing, Pawn, Piece, PieceType, Player, PlayerTime, Position, PositionState, Queen, Resignation, Rook, Stalemate, ThreefoldRepetition, TimeControl, TimeOut, White}
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
        .append("timeControl", serializeTimeControl(game.timeControl))
        .append("whiteTime", serializePlayerTime(game.whiteTime))
        .append("blackTime", serializePlayerTime(game.blackTime))

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

        // Deserialize time control and player times
        val timeControl = deserializeTimeControl(doc.getString("timeControl"))
        val whiteTime = deserializePlayerTime(doc.getString("whiteTime"))
        val blackTime = deserializePlayerTime(doc.getString("blackTime"))

        // Deserialize game result
        val gameResult = parseGameResult(doc.getString("result"))

        Some(PositionState(
          board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id),
          timeControl, whiteTime, blackTime,
          halfmovesSinceLastCaptureOrPawn = 0,
          positionHistory = List.empty,
          hasWhiteResigned = false,
          hasBlackResigned = false,
          gameResult = gameResult
        ))
      } else {
        None
      }
    }
  }

  override def update(id: String, game: PositionState): Future[Boolean] = {
    Future {
      val boardJson = serializeBoard(game.board)
      val resultStr = game.gameResult match {
        case Ongoing => null
        case Checkmate(winner) => s"checkmate:$winner"
        case Draw(reason) => s"draw:${drawReasonToString(reason)}"
        case Resignation(winner) => s"resignation:$winner"
        case TimeOut(winner) => s"timeout:$winner"
      }
      val result = collection.updateOne(
        Filters.eq("id", id),
        new Document("$set", new Document()
          .append("turn", game.turn.toString)
          .append("boardState", boardJson)
          .append("result", resultStr)
          .append("lastModified", new java.util.Date())
          .append("timeControl", serializeTimeControl(game.timeControl))
          .append("whiteTime", serializePlayerTime(game.whiteTime))
          .append("blackTime", serializePlayerTime(game.blackTime)))
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

  override def listSummaries(): Future[List[(String, String, Int, Boolean)]] = {
    Future {
      // Use projection to only fetch id and turn, not boardState or other large fields
      val projection = new Document()
        .append("id", 1)
        .append("turn", 1)
        .append("_id", 0)
      collection.find().projection(projection).asScala.map { doc =>
        val gameId = doc.getString("id")
        val turn = doc.getString("turn")
        // Count moves without fetching them - use move count from metadata if available
        // For now, we'll estimate from the document or return 0
        val moveCount = 0 // TODO: Store move count in game document for efficiency
        val isGameOver = false // TODO: Store game over status in game document for efficiency
        (gameId, turn, moveCount, isGameOver)
      }.toList
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

        val gameResult = parseGameResult(doc.getString("result"))

        PositionState(
          board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(gameId),
          timeControl = None, whiteTime = None, blackTime = None,
          halfmovesSinceLastCaptureOrPawn = 0,
          positionHistory = List.empty,
          hasWhiteResigned = false,
          hasBlackResigned = false,
          gameResult = gameResult
        )
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

        val gameResult = parseGameResult(doc.getString("result"))

        Some(PositionState(
          board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(gameId),
          timeControl = None, whiteTime = None, blackTime = None,
          halfmovesSinceLastCaptureOrPawn = 0,
          positionHistory = List.empty,
          hasWhiteResigned = false,
          hasBlackResigned = false,
          gameResult = gameResult
        ))
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

  private def serializeTimeControl(tc: Option[TimeControl]): String = {
    tc match {
      case Some(t) => s"${t.initialTimeMs},${t.incrementMs},${t.delayMs}"
      case None => null
    }
  }

  private def deserializeTimeControl(str: String): Option[TimeControl] = {
    if (str == null || str.isEmpty) None
    else {
      val parts = str.split(",")
      Some(TimeControl(parts(0).toLong, parts(1).toLong, parts(2).toLong))
    }
  }

  private def serializePlayerTime(pt: Option[PlayerTime]): String = {
    pt match {
      case Some(t) => s"${t.remainingTimeMs},${t.lastUpdatedAt}"
      case None => null
    }
  }

  private def deserializePlayerTime(str: String): Option[PlayerTime] = {
    if (str == null || str.isEmpty) None
    else {
      val parts = str.split(",")
      Some(PlayerTime(parts(0).toLong, parts(1).toLong))
    }
  }

  private def parseGameResult(resultStr: String): model.GameResult = {
    if (resultStr == null || resultStr.isEmpty) Ongoing
    else if (resultStr.startsWith("checkmate:")) {
      val winner = parseColor(resultStr.substring(10))
      Checkmate(winner)
    } else if (resultStr.startsWith("draw:")) {
      val reasonStr = resultStr.substring(5)
      val reason: DrawReason = reasonStr match {
        case "stalemate" => Stalemate
        case "insufficient_material" => InsufficientMaterial
        case "threefold_repetition" => ThreefoldRepetition
        case "fifty_move_rule" => FiftyMoveRule
        case "mutual_agreement" => MutualAgreement
        case "dead_position" => DeadPosition
        case _ => Stalemate // Fallback
      }
      Draw(reason)
    } else if (resultStr.startsWith("resignation:")) {
      val winner = parseColor(resultStr.substring(12))
      Resignation(winner)
    } else if (resultStr.startsWith("timeout:")) {
      val winner = parseColor(resultStr.substring(8))
      TimeOut(winner)
    } else Ongoing // Fallback for invalid format
  }

  private def drawReasonToString(reason: DrawReason): String = reason match {
    case Stalemate => "stalemate"
    case InsufficientMaterial => "insufficient_material"
    case ThreefoldRepetition => "threefold_repetition"
    case FiftyMoveRule => "fifty_move_rule"
    case MutualAgreement => "mutual_agreement"
    case DeadPosition => "dead_position"
  }
}
