package slick

import dao.MoveDao
import _root_.model.{Move, Position}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Slick implementation of MoveDao. */
class SlickMoveDao(db: Database, tables: Tables)(implicit ec: ExecutionContext) extends MoveDao {
  import tables.profile.api._

  override def create(move: Move, gameId: String, moveIndex: Int): Future[Int] = {
    val action = (tables.moves returning tables.moves.map(_.id)) += 
      (0, gameId, moveIndex, move.from.col, move.from.row, move.to.col, move.to.row)
    db.run(action)
  }

  override def findById(id: Int): Future[Option[Move]] = {
    val action = tables.moves.filter(_.id === id).result.headOption
    db.run(action).map(_.map { case (_, _, _, fromCol, fromRow, toCol, toRow) =>
      Move(Position(fromCol, fromRow), Position(toCol, toRow))
    })
  }

  override def findByGameId(gameId: String): Future[List[Move]] = {
    val action = tables.moves
      .filter(_.gameId === gameId)
      .sortBy(_.moveIndex.asc)
      .result
    db.run(action).map(_.map { case (_, _, _, fromCol, fromRow, toCol, toRow) =>
      Move(Position(fromCol, fromRow), Position(toCol, toRow))
    }.toList)
  }

  override def deleteByGameId(gameId: String): Future[Int] = {
    val action = tables.moves.filter(_.gameId === gameId).delete
    db.run(action)
  }
}
