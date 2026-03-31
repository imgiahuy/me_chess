package chess.domain

import org.scalatest.funsuite.AnyFunSuite
import scala.util.{Try, Success, Failure}

class BoardSpec extends AnyFunSuite {

  test("initial board should have 32 pieces") {
    val board = Board.initial
    assert(board.allPieces.size == 32)
  }

  test("initial board should have correct number of white and black pieces") {
    val board = Board.initial
    assert(board.piecesOf(White).size == 16)
    assert(board.piecesOf(Black).size == 16)
  }

  test("initial board should place rooks correctly") {
    val board = Board.initial
    assert(board.pieceAt(Position(0, 0)).contains(Piece(White, Rook)))
    assert(board.pieceAt(Position(7, 0)).contains(Piece(White, Rook)))
    assert(board.pieceAt(Position(0, 7)).contains(Piece(Black, Rook)))
    assert(board.pieceAt(Position(7, 7)).contains(Piece(Black, Rook)))
  }

  test("isEmpty should return true for empty square") {
    val board = Board.initial
    assert(board.isEmpty(Position(4, 4)))
  }

  test("isEmpty should return false for occupied square") {
    val board = Board.initial
    assert(!board.isEmpty(Position(0, 0)))
  }

  test("withMove should move a piece to a new position") {
    val board = Board.initial
    val newBoard = board.withMove(Position(0, 1), Position(0, 3)) match {
      case Success(b) => b
      case Failure(ex) => fail(s"Unexpected move failure: $ex")
    }

    assert(newBoard.pieceAt(Position(0, 3)).contains(Piece(White, Pawn)))
    assert(newBoard.isEmpty(Position(0, 1)))
  }

  test("withMove should not mutate original board") {
    val board = Board.initial
    val newBoard = board.withMove(Position(0, 1), Position(0, 3)) match {
      case Success(b) => b
      case Failure(ex) => fail(s"Unexpected move failure: $ex")
    }

    assert(board.pieceAt(Position(0, 1)).contains(Piece(White, Pawn)))
    assert(board.isEmpty(Position(0, 3)))
  }

  test("withMove should return same board if source is empty") {
    val board = Board.initial
    val newBoard = board.withMove(Position(4, 4), Position(4, 5)) match {
      case Success(b) => b
      case Failure(ex) => fail(s"Unexpected move failure: $ex")
    }

    assert(newBoard == board)
  }

  test("withMove should capture opponent piece") {
    val board = Board.empty.copy(
      squares = Map(
        Position(0, 0) -> Piece(White, Rook),
        Position(0, 1) -> Piece(Black, Pawn)
      )
    )

    val newBoard = board.withMove(Position(0, 0), Position(0, 1)) match {
      case Success(b) => b
      case Failure(ex) => fail(s"Unexpected move failure: $ex")
    }

    assert(newBoard.pieceAt(Position(0, 1)).contains(Piece(White, Rook)))
    assert(newBoard.pieceAt(Position(0, 0)).isEmpty)
  }

  test("kingsAlive should return both colors in initial position") {
    val board = Board.initial
    assert(board.kingsAlive == Set(White, Black))
  }

  test("kingsAlive should reflect captured king") {
    val board = Board.empty.copy(
      squares = Map(
        Position(4, 0) -> Piece(White, King)
      )
    )

    assert(board.kingsAlive == Set(White))
  }

}