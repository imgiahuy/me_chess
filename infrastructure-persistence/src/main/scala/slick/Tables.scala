package slick

import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile
import slick.jdbc.PostgresProfile

/** Profile-agnostic Slick table definitions for the chess database schema. */
class Tables(val profile: JdbcProfile) {
  import profile.api._

  /** Players table - stores player information. */
  class Players(tag: Tag) extends Table[(Int, String, Int)](tag, "players") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def elo = column[Int]("elo", O.Default(1200))

    def * = (id, name, elo)
  }

  val players = TableQuery[Players]

  /** Games table - stores game state and metadata. */
  class Games(tag: Tag) extends Table[(String, Int, Int, String, java.time.LocalDate, String, scala.Option[String], scala.Option[String], scala.Option[String], scala.Option[String], Int, Boolean)](tag, "games") {
    def id = column[String]("id", O.PrimaryKey)
    def whitePlayerId = column[Int]("white_player_id")
    def blackPlayerId = column[Int]("black_player_id")
    def turn = column[String]("turn") // "White" or "Black"
    def creationDate = column[java.time.LocalDate]("creation_date")
    def boardState = column[String]("board_state") // JSON serialized Board
    def result = column[scala.Option[String]]("result") // Optional game result
    def timeControl = column[scala.Option[String]]("time_control") // JSON serialized TimeControl
    def whiteTime = column[scala.Option[String]]("white_time") // JSON serialized PlayerTime
    def blackTime = column[scala.Option[String]]("black_time") // JSON serialized PlayerTime
    def moveCount = column[Int]("move_count", O.Default(0))
    def isGameOver = column[Boolean]("is_game_over", O.Default(false))

    def * = (id, whitePlayerId, blackPlayerId, turn, creationDate, boardState, result, timeControl, whiteTime, blackTime, moveCount, isGameOver)

    // Foreign key relationships
    def whitePlayer = foreignKey("white_player_fk", whitePlayerId, players)(_.id)
    def blackPlayer = foreignKey("black_player_fk", blackPlayerId, players)(_.id)
  }

  val games = TableQuery[Games]

  /** Moves table - stores move history for games. */
  class Moves(tag: Tag) extends Table[(Int, String, Int, Int, Int, Int, Int, scala.Option[String])](tag, "moves") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def gameId = column[String]("game_id")
    def moveIndex = column[Int]("move_index") // Order of moves in the game
    def fromCol = column[Int]("from_col")
    def fromRow = column[Int]("from_row")
    def toCol = column[Int]("to_col")
    def toRow = column[Int]("to_row")
    def specialMove = column[scala.Option[String]]("special_move")

    def * = (id, gameId, moveIndex, fromCol, fromRow, toCol, toRow, specialMove)

    // Foreign key relationship
    def game = foreignKey("game_fk", gameId, games)(_.id)
  }

  val moves = TableQuery[Moves]

  /** Schema for all tables. */
  val schema = players.schema ++ games.schema ++ moves.schema
}

/** H2-specific table definitions. */
object H2Tables extends Tables(H2Profile)

/** PostgreSQL-specific table definitions. */
object PostgresTables extends Tables(PostgresProfile)
