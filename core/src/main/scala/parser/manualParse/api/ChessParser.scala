package parser.manualParse.api

import model.*
import parser.Input.{ModelSnap, Pgn}
import parser.{Input, Output, ParserInterface}

class ChessParser extends ParserInterface {

  // -----------------------------
  // Public API
  override def parse(input: Input): Output = (input) match {

    case Input.Fen(fen) =>
      parseFen(fen)
    case Input.Uci(moveStr) =>
      parseUci(moveStr)
    case Input.Pgn(moves) =>
      parsePgn(moves)
    case _ =>
      Output.InvalidOutput("Parse is only supported for Fen, Uci, and Pgn inputs.")
  }

  override def reverse(input: Input): String = input match {

    case Input.ModelSnap(snapshot) =>
      parseBoardToFen(snapshot)
    case Input.ModelMove(move) =>
      parseMoveToUci(move)
    case Input.ListMoves(snapshot) =>
      parseListMovesToPgn(snapshot)
    case _ =>
      throw new Exception("Reverse is only supported for ModelBoard, ModelMove, and ListMoves inputs.")
  }

  // -----------------------------
  // ── Private Helpers ───────────────────────────────────────────────────────────
  private def parseBoardToFen(snapshot: Snapshot): String = {
    val boardStr = (7 to 0 by -1).map { row =>
      var empty = 0

      val rowStr = (0 to 7).flatMap { col =>
        snapshot.board.pieceAt(Position(col, row)) match {
          case Some(p) =>
            val c = PieceType.toAbbrev(p.pieceType)
            val symbol = if (p.color == White) c else c.toLower

            val prefix =
              if (empty > 0) {
                val e = empty.toString
                empty = 0
                e
              } else ""

            prefix + symbol

          case None =>
            empty += 1
            ""
        }
      }.mkString

      if (empty > 0) rowStr + empty else rowStr
    }.mkString("/")

    val turnStr = snapshot.turn match {
      case White => "w"
      case Black => "b"
    }

    // For simplicity, we use "-" for castling availability and en passant target square, and "0 1" for half move clock and full move number.
    s"$boardStr $turnStr - - 0 1"

  }

  private def parseMoveToUci(move: Move): String = {
    val from = move.from
    val to = move.to
    if (from.isValid && to.isValid) {
      s"${('a' + from.col).toChar}${from.row + 1}${('a' + to.col).toChar}${to.row + 1}"
    } else {
      throw new Exception("Invalid move positions")
    }
  }


  private def parseFen(fen: String): Output = {
    val parts = fen.split(" ")

    if (parts.length < 2)
      throw new Exception("Invalid FEN")

    val rows = parts(0).split("/")

    val squares = rows.zipWithIndex.flatMap { case (rowStr, rowIdx) =>
      var col = 0

      rowStr.flatMap { c =>
        if (c.isDigit) {
          col += c.asDigit
          None
        } else {
          val color = if (c.isUpper) White else Black
          val pieceType = PieceType.fromAbbrev(c.toUpper)
          val pos = Position(col, 7 - rowIdx)
          col += 1
          Some(pos -> Piece(color, pieceType))
        }
      }
    }.toMap

    val turn =
      parts(1) match {
        case "w" => White
        case "b" => Black
        case _   => throw new Exception("Invalid turn")
      }

    Output.fenToModel(Board(squares), turn)
  }

  private def parseUci(input: String): Output = {

    val cleaned =
      input.trim.toLowerCase.replaceAll("[\\s\\-]+", "")

    if (cleaned.length != 4)
      return Output.InvalidOutput("UCI move must be 4 characters long")

    def fileToCol(c: Char): Option[Int] =
      if (c >= 'a' && c <= 'h') Some(c - 'a')
      else None

    def rankToRow(c: Char): Option[Int] =
      if (c >= '1' && c <= '8') Some(c - '1')
      else None

    val result =
      for {
        fromCol <- fileToCol(cleaned(0))
        fromRow <- rankToRow(cleaned(1))
        toCol <- fileToCol(cleaned(2))
        toRow <- rankToRow(cleaned(3))
      } yield Move(
        Position(fromCol, fromRow),
        Position(toCol, toRow)
      )

    result match {
      case Some(move) => Output.uciToModel(move)
      case None => Output.InvalidOutput("Invalid UCI format")
    }
  }
  
  private def parsePgn(moves: String): Output = {
    val moveList = moves.split("\\s+").toList.flatMap { moveStr =>
      parseUci(moveStr) match {
        case Output.uciToModel(move) => Some(move)
        case _                     => None
      }
    }

    Output.pgnToModel(moveList)
  }
  
  private def parseListMovesToPgn(snapshot: Snapshot): String = {
    snapshot.moveHistory.map(parseMoveToUci).mkString(" ")
  }
}
