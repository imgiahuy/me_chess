package slick

import dao.GameDao
import _root_.model.{Board, Black, Bishop, Checkmate, Color, DeadPosition, Draw, DrawReason, FiftyMoveRule, GameResult => MGameResult, InsufficientMaterial, King, Knight, Move, MutualAgreement, Ongoing, Pawn, Piece, PieceType, Player, PlayerTime, Position, PositionState, Queen, Resignation, Rook, Stalemate, ThreefoldRepetition, TimeControl, TimeOut, White}
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
      val timeControlStr = serializeTimeControl(game.timeControl)
      val whiteTimeStr = serializePlayerTime(game.whiteTime)
      val blackTimeStr = serializePlayerTime(game.blackTime)

      val action = (tables.games returning tables.games.map(_.id)) +=
        (gameId, whiteId, blackId, game.turn.toString, game.creationDate, boardJson, None, timeControlStr, whiteTimeStr, blackTimeStr)

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
      case Some((_, whiteId, blackId, turnStr, creationDate, boardJson, resultOpt, timeControlStr, whiteTimeStr, blackTimeStr)) =>
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
            gameResult = parseGameResult(resultOpt)
            timeControl = deserializeTimeControl(timeControlStr)
            whiteTime = deserializePlayerTime(whiteTimeStr)
            blackTime = deserializePlayerTime(blackTimeStr)
          } yield PositionState(
            board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id),
            timeControl = timeControl, whiteTime = whiteTime, blackTime = blackTime,
            halfmovesSinceLastCaptureOrPawn = 0,
            positionHistory = List.empty,
            hasWhiteResigned = false,
            hasBlackResigned = false,
            gameResult = gameResult
          )
        }
      case None => Future.successful(None)
    }
  }

  override def update(id: String, game: PositionState): Future[Boolean] = {
    val boardJson = serializeBoard(game.board)
    val resultStr = game.gameResult match {
      case Ongoing => None
      case Checkmate(winner) => Some(s"checkmate:$winner")
      case Draw(reason) => Some(s"draw:${drawReasonToString(reason)}")
      case Resignation(winner) => Some(s"resignation:$winner")
      case TimeOut(winner) => Some(s"timeout:$winner")
    }
    val timeControlStr = serializeTimeControl(game.timeControl)
    val whiteTimeStr = serializePlayerTime(game.whiteTime)
    val blackTimeStr = serializePlayerTime(game.blackTime)
    val action = tables.games
      .filter(_.id === id)
      .map(g => (g.turn, g.boardState, g.result, g.timeControl, g.whiteTime, g.blackTime))
      .update((game.turn.toString, boardJson, resultStr, timeControlStr, whiteTimeStr, blackTimeStr))

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
      Future.sequence(gameRows.map { case (id, whiteId, blackId, turnStr, creationDate, boardJson, resultOpt, timeControlStr, whiteTimeStr, blackTimeStr) =>
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
            gameResult = parseGameResult(resultOpt)
            timeControl = deserializeTimeControl(timeControlStr)
            whiteTime = deserializePlayerTime(whiteTimeStr)
            blackTime = deserializePlayerTime(blackTimeStr)
          } yield PositionState(
            board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id),
            timeControl = timeControl, whiteTime = whiteTime, blackTime = blackTime,
            halfmovesSinceLastCaptureOrPawn = 0,
            positionHistory = List.empty,
            hasWhiteResigned = false,
            hasBlackResigned = false,
            gameResult = gameResult
          )
        }
      }).map(_.flatten.toList)
    }
  }

  override def findLatest(): Future[Option[PositionState]] = {
    val action = tables.games.sortBy(_.creationDate.desc).result.headOption
    db.run(action).flatMap {
      case Some((id, whiteId, blackId, turnStr, creationDate, boardJson, resultOpt, timeControlStr, whiteTimeStr, blackTimeStr)) =>
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
            gameResult = parseGameResult(resultOpt)
            timeControl = deserializeTimeControl(timeControlStr)
            whiteTime = deserializePlayerTime(whiteTimeStr)
            blackTime = deserializePlayerTime(blackTimeStr)
          } yield PositionState(
            board, turn, moves, whitePlayer, blackPlayer, creationDate, Some(id),
            timeControl = timeControl, whiteTime = whiteTime, blackTime = blackTime,
            halfmovesSinceLastCaptureOrPawn = 0,
            positionHistory = List.empty,
            hasWhiteResigned = false,
            hasBlackResigned = false,
            gameResult = gameResult
          )
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

  private def parseGameResult(resultOpt: Option[String]): MGameResult = {
    resultOpt match {
      case None => Ongoing
      case Some(str) if str.startsWith("checkmate:") =>
        val winner = parseColor(str.substring(10))
        Checkmate(winner)
      case Some(str) if str.startsWith("draw:") =>
        val reasonStr = str.substring(5)
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
      case Some(str) if str.startsWith("resignation:") =>
        val winner = parseColor(str.substring(12))
        Resignation(winner)
      case Some(str) if str.startsWith("timeout:") =>
        val winner = parseColor(str.substring(8))
        TimeOut(winner)
      case Some(other) => Ongoing // Fallback for invalid format
    }
  }

  private def drawReasonToString(reason: DrawReason): String = reason match {
    case Stalemate => "stalemate"
    case InsufficientMaterial => "insufficient_material"
    case ThreefoldRepetition => "threefold_repetition"
    case FiftyMoveRule => "fifty_move_rule"
    case MutualAgreement => "mutual_agreement"
    case DeadPosition => "dead_position"
  }

  private def serializeTimeControl(tc: Option[TimeControl]): Option[String] = {
    tc match {
      case Some(t) => Some(s"${t.initialTimeMs},${t.incrementMs},${t.delayMs}")
      case None => None
    }
  }

  private def deserializeTimeControl(strOpt: Option[String]): Option[TimeControl] = {
    strOpt match {
      case None => None
      case Some(str) if str.isEmpty => None
      case Some(str) =>
        val parts = str.split(",")
        Some(TimeControl(parts(0).toLong, parts(1).toLong, parts(2).toLong))
    }
  }

  private def serializePlayerTime(pt: Option[PlayerTime]): Option[String] = {
    pt match {
      case Some(t) => Some(s"${t.remainingTimeMs},${t.lastUpdatedAt}")
      case None => None
    }
  }

  private def deserializePlayerTime(strOpt: Option[String]): Option[PlayerTime] = {
    strOpt match {
      case None => None
      case Some(str) if str.isEmpty => None
      case Some(str) =>
        val parts = str.split(",")
        Some(PlayerTime(parts(0).toLong, parts(1).toLong))
    }
  }
}
