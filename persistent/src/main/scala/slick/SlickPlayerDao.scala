package slick

import dao.PlayerDao
import _root_.model.Player
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/** Slick implementation of PlayerDao. */
class SlickPlayerDao(db: Database, tables: Tables)(implicit ec: ExecutionContext) extends PlayerDao {
  import tables.profile.api._

  override def create(player: Player): Future[Int] = {
    val action = (tables.players returning tables.players.map(_.id)) += (0, player.name, 1200)
    db.run(action)
  }

  override def findById(id: Int): Future[Option[Player]] = {
    val action = tables.players.filter(_.id === id).result.headOption
    db.run(action).map(_.map { case (_, name, _) => Player(name) })
  }

  override def findByName(name: String): Future[Option[Player]] = {
    val action = tables.players.filter(_.name === name).result.headOption
    db.run(action).map {
      case Some((_, playerName, _)) => Some(Player(playerName))
      case None => None
    }
  }

  override def update(id: Int, player: Player): Future[Boolean] = {
    val action = tables.players.filter(_.id === id).map(_.name).update(player.name)
    db.run(action).map(_ > 0)
  }

  override def delete(id: Int): Future[Boolean] = {
    val action = tables.players.filter(_.id === id).delete
    db.run(action).map(_ > 0)
  }

  override def findAll(): Future[List[Player]] = {
    val action = tables.players.result
    db.run(action).map(_.map { case (_, name, _) => Player(name) }.toList)
  }

  override def getOrCreateId(name: String): Future[Int] = {
    val action = tables.players.filter(_.name === name).result.headOption
    db.run(action).flatMap {
      case Some((id, _, _)) => Future.successful(id)
      case None => create(Player(name))
    }
  }

  override def updateElo(id: Int, newElo: Int): Future[Boolean] = {
    val action = tables.players.filter(_.id === id).map(_.elo).update(newElo)
    db.run(action).map(_ > 0)
  }

  override def findLeaderboard(): Future[List[(Player, Int)]] = {
    val action = tables.players.sortBy(_.elo.desc).result
    db.run(action).map(_.map { case (_, name, elo) => (Player(name), elo) }.toList)
  }
}
