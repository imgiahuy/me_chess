package parser

import model._

object FEN {

  def boardToFEN(board: Board): String = {
    (7 to 0 by -1).map { row =>
      var empty = 0

      val rowStr = (0 to 7).flatMap { col =>
        board.pieceAt(Position(col, row)) match {
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
  }

  def toFEN(board: Board, turn: Color): String = {
    val colorStr = if (turn == White) "w" else "b"
    s"${boardToFEN(board)} $colorStr"
  }

  def fromFEN(fen: String): (Board, Color) = {
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

    val turn = parts(1) match {
      case "w" => White
      case "b" => Black
      case _   => throw new Exception("Invalid turn in FEN")
    }

    (Board(squares), turn)
  }
}