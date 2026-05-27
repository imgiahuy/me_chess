package slick

import slick.jdbc.JdbcProfile

/** Profile-agnostic Slick table definitions for the chess database schema. */
class Tables(val profile: JdbcProfile) {
  import profile.api._

  /** Players table - stores player information. */
  class Players(tag: Tag) extends Table[(Int, String)](tag, "players") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")

    def * = (id, name)
  }

  val players = TableQuery[Players]

  /** Games table - stores game state and metadata. */
  class Games(tag: Tag) extends Table[(String, Int, Int, String, java.time.LocalDate, String, Option[String])](tag, "games") {
    def id = column[String]("id", O.PrimaryKey)
    def whitePlayerId = column[Int]("white_player_id")
    def blackPlayerId = column[Int]("black_player_id")
    def turn = column[String]("turn") // "White" or "Black"
    def creationDate = column[java.time.LocalDate]("creation_date")
    def boardState = column[String]("board_state") // JSON serialized Board
    def result = column[Option[String]]("result") // Optional game result

    def * = (id, whitePlayerId, blackPlayerId, turn, creationDate, boardState, result)

    // Foreign key relationships
    def whitePlayer = foreignKey("white_player_fk", whitePlayerId, players)(_.id)
    def blackPlayer = foreignKey("black_player_fk", blackPlayerId, players)(_.id)
  }

  val games = TableQuery[Games]

  /** Moves table - stores move history for games. */
  class Moves(tag: Tag) extends Table[(Int, String, Int, Int, Int, Int, Int)](tag, "moves") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def gameId = column[String]("game_id")
    def moveIndex = column[Int]("move_index") // Order of moves in the game
    def fromCol = column[Int]("from_col")
    def fromRow = column[Int]("from_row")
    def toCol = column[Int]("to_col")
    def toRow = column[Int]("to_row")

    def * = (id, gameId, moveIndex, fromCol, fromRow, toCol, toRow)

    // Foreign key relationship
    def game = foreignKey("game_fk", gameId, games)(_.id)
  }

  val moves = TableQuery[Moves]

  /** Schema for all tables. */
  val schema = players.schema ++ games.schema ++ moves.schema
}

/** H2-specific table definitions. */
object H2Tables extends Tables(slick.jdbc.H2Profile)

/** PostgreSQL-specific table definitions. */
object PostgresTables extends Tables(slick.jdbc.PostgresProfile)
