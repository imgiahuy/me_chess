package slick

import dao.GameDao
import _root_.model.{Board, Black, Bishop, Color, King, Knight, Move, Pawn, Piece, PieceType, Player, Position, PositionState, Queen, Rook, White}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Slick implementation of GameDao. */
class SlickGameDao(db: Database, tables: Tables, playerDao: SlickPlayerDao, moveDao: SlickMoveDao)(implicit ec: ExecutionContext) extends GameDao {
  import tables.profile.api._

  override def create(game: PositionState): Future[String] = {
    // First ensure players exist and get their IDs
    val setup = for {
      whiteId <- playerDao.getOrCreateId(game.whitePlayer.name)
      blackId <- playerDao.getOrCreateId(game.blackPlayer.name)
    } yield (whiteId, blackId)

    setup.flatMap { case (whiteId, blackId) =>
      val gameId = java.util.UUID.randomUUID().toString
      val boardJson = serializeBoard(game.board)
      
      val action = (tables.games returning tables.games.map(_.id)) += 
        (gameId, whiteId, blackId, game.turn.toString, game.creationDate, boardJson, None)
      
      db.run(action).flatMap { _ =>
        // Insert moves
        val moveActions = game.moveHistory.zipWithIndex.map { case (move, index) =>
          moveDao.create(move, gameId, index)
        }
        Future.sequence(moveActions).map(_ => gameId)
      }
    }
  }

  override def findById(id: String): Future[Option[PositionState]] = {
    val action = tables.games.filter(_.id === id).result.headOption
    db.run(action).flatMap {
      case Some((_, whiteId, blackId, turnStr, creationDate, boardJson, _)) =>
        for {
          whitePlayerOpt <- playerDao.findById(whiteId)
          blackPlayerOpt <- playerDao.findById(blackId)
          moves <- moveDao.findByGameId(id)
        } yield {
          for {
            whitePlayer <- whitePlayerOpt
            blackPlayer <- blackPlayerOpt
            board = deserializeBoard(boardJson)
            turn = parseColor(turnStr)
          } yield PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id))
        }
      case None => Future.successful(None)
    }
  }

  override def update(id: String, game: PositionState): Future[Boolean] = {
    val boardJson = serializeBoard(game.board)
    val action = tables.games
      .filter(_.id === id)
      .map(g => (g.turn, g.boardState))
      .update((game.turn.toString, boardJson))
    
    db.run(action).flatMap { updated =>
      if (updated > 0) {
        // Update moves: delete old ones and insert new ones
        moveDao.deleteByGameId(id).flatMap { _ =>
          val moveActions = game.moveHistory.zipWithIndex.map { case (move, index) =>
            moveDao.create(move, id, index)
          }
          Future.sequence(moveActions).map(_ => true)
        }
      } else {
        Future.successful(false)
      }
    }
  }

  override def delete(id: String): Future[Boolean] = {
    // First delete moves, then the game
    for {
      movesDeleted <- moveDao.deleteByGameId(id)
      gameDeleted <- db.run(tables.games.filter(_.id === id).delete)
    } yield gameDeleted > 0
  }

  override def listAll(): Future[List[String]] = {
    val action = tables.games.map(_.id).result
    db.run(action).map(_.toList)
  }

  override def listSummaries(): Future[List[(String, String, Int, Boolean)]] = {
    val action = tables.games.map(g => (g.id, g.turn)).result
    db.run(action).map { rows =>
      rows.map { case (id, turn) =>
        // TODO: Store move count and game over status in game document for efficiency
        val moveCount = 0
        val isGameOver = false
        (id, turn, moveCount, isGameOver)
      }.toList
    }
  }

  override def findAll(): Future[List[PositionState]] = {
    val action = tables.games.result
    db.run(action).flatMap { gameRows =>
      Future.sequence(gameRows.map { case (id, whiteId, blackId, turnStr, creationDate, boardJson, _) =>
        for {
          whitePlayerOpt <- playerDao.findById(whiteId)
          blackPlayerOpt <- playerDao.findById(blackId)
          moves <- moveDao.findByGameId(id)
        } yield {
          for {
            whitePlayer <- whitePlayerOpt
            blackPlayer <- blackPlayerOpt
            board = deserializeBoard(boardJson)
            turn = parseColor(turnStr)
          } yield PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id))
        }
      }).map(_.flatten.toList)
    }
  }

  override def findLatest(): Future[Option[PositionState]] = {
    val action = tables.games.sortBy(_.creationDate.desc).result.headOption
    db.run(action).flatMap {
      case Some((id, whiteId, blackId, turnStr, creationDate, boardJson, _)) =>
        for {
          whitePlayerOpt <- playerDao.findById(whiteId)
          blackPlayerOpt <- playerDao.findById(blackId)
          moves <- moveDao.findByGameId(id)
        } yield {
          for {
            whitePlayer <- whitePlayerOpt
            blackPlayer <- blackPlayerOpt
            board = deserializeBoard(boardJson)
            turn = parseColor(turnStr)
          } yield PositionState(board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id))
        }
      case None => Future.successful(None)
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
