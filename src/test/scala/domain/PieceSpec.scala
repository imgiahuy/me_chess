package chess.domain

import org.scalatest.funsuite.AnyFunSuite

class PieceSpec extends AnyFunSuite {

  test("white pieces should have uppercase symbols") {
    assert(Piece(White, King).symbol == 'K')
    assert(Piece(White, Queen).symbol == 'Q')
    assert(Piece(White, Rook).symbol == 'R')
    assert(Piece(White, Bishop).symbol == 'B')
    assert(Piece(White, Knight).symbol == 'N')
    assert(Piece(White, Pawn).symbol == 'P')
  }

  test("black pieces should have lowercase symbols") {
    assert(Piece(Black, King).symbol == 'k')
    assert(Piece(Black, Queen).symbol == 'q')
    assert(Piece(Black, Rook).symbol == 'r')
    assert(Piece(Black, Bishop).symbol == 'b')
    assert(Piece(Black, Knight).symbol == 'n')
    assert(Piece(Black, Pawn).symbol == 'p')
  }

  test("symbol should differ only by case between colors") {
    val whitePawn = Piece(White, Pawn).symbol
    val blackPawn = Piece(Black, Pawn).symbol

    assert(whitePawn.toLower == blackPawn)
  }

  test("toString should return readable format") {
    val piece = Piece(White, Queen)
    assert(piece.toString == "White Queen")
  }

  test("pieces with same color and type should be equal") {
    val p1 = Piece(White, Rook)
    val p2 = Piece(White, Rook)

    assert(p1 == p2)
  }

  test("pieces with different color or type should not be equal") {
    val p1 = Piece(White, Rook)
    val p2 = Piece(Black, Rook)
    val p3 = Piece(White, Bishop)

    assert(p1 != p2)
    assert(p1 != p3)
  }

}