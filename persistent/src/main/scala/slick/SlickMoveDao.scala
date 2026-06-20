package slick

import dao.MoveDao
import _root_.model.{Bishop, CastlingKingSide, CastlingQueenSide, EnPassant, Knight, Move, Position, Promotion, Queen, Rook, SpecialMoveType}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Slick implementation of MoveDao. */
class SlickMoveDao(db: Database, tables: Tables)(implicit ec: ExecutionContext) extends MoveDao {
  import tables.profile.api._

  private def serializeSpecialMove(sm: Option[SpecialMoveType]): Option[String] = sm match {
    case None                     => None
    case Some(CastlingKingSide)   => Some("castling_kingside")
    case Some(CastlingQueenSide)  => Some("castling_queenside")
    case Some(EnPassant)          => Some("en_passant")
    case Some(Promotion(Queen))   => Some("promotion_queen")
    case Some(Promotion(Rook))    => Some("promotion_rook")
    case Some(Promotion(Bishop))  => Some("promotion_bishop")
    case Some(Promotion(Knight))  => Some("promotion_knight")
    case Some(Promotion(_))       => Some("promotion_queen")
  }

  private def deserializeSpecialMove(s: Option[String]): Option[SpecialMoveType] = s match {
    case None | Some("")                    => None
    case Some("castling_kingside")          => Some(CastlingKingSide)
    case Some("castling_queenside")         => Some(CastlingQueenSide)
    case Some("en_passant")                 => Some(EnPassant)
    case Some("promotion_queen")            => Some(Promotion(Queen))
    case Some("promotion_rook")             => Some(Promotion(Rook))
    case Some("promotion_bishop")           => Some(Promotion(Bishop))
    case Some("promotion_knight")           => Some(Promotion(Knight))
    case _                                  => None
  }

  override def create(move: Move, gameId: String, moveIndex: Int): Future[Int] = {
    val action = (tables.moves returning tables.moves.map(_.id)) +=
      (0, gameId, moveIndex, move.from.col, move.from.row, move.to.col, move.to.row, serializeSpecialMove(move.specialMove))
    db.run(action)
  }

  override def findById(id: Int): Future[Option[Move]] = {
    val action = tables.moves.filter(_.id === id).result.headOption
    db.run(action).map(_.map { case (_, _, _, fromCol, fromRow, toCol, toRow, smStr) =>
      Move(Position(fromCol, fromRow), Position(toCol, toRow), deserializeSpecialMove(smStr))
    })
  }

  override def findByGameId(gameId: String): Future[List[Move]] = {
    val action = tables.moves
      .filter(_.gameId === gameId)
      .sortBy(_.moveIndex.asc)
      .result
    db.run(action).map(_.map { case (_, _, _, fromCol, fromRow, toCol, toRow, smStr) =>
      Move(Position(fromCol, fromRow), Position(toCol, toRow), deserializeSpecialMove(smStr))
    }.toList)
  }

  override def deleteByGameId(gameId: String): Future[Int] = {
    val action = tables.moves.filter(_.gameId === gameId).delete
    db.run(action)
  }
}
