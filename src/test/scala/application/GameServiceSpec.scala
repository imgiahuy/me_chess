package chess.application

import org.scalatest.funsuite.AnyFunSuite
import chess.domain.*
import domain.engine.GameState
import domain.model.{Black, Board, King, Move, Pawn, Piece, Position, White}

class GameServiceSpec extends AnyFunSuite {

  // --- applyMove + validate ------------------------------------------------

  test("validate should succeed for legal move") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 3)) // e2 -> e4

    val result = GameService.validate(state, move)
    assert(result.isRight)
  }

  test("validate fails if source square is empty") {
    val state = GameState.initial
    val move = Move(Position(4, 3), Position(4, 4)) // e4 -> e5 (empty)

    val result = GameService.validate(state, move)
    assert(result.isLeft)
    assert(result.left.get.contains("No piece at e4"))
  }

  test("validate fails if source and destination are the same") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 1))

    val result = GameService.validate(state, move)
    assert(result.isLeft)
    assert(result.left.get.contains("Source and destination are the same square"))
  }

  test("validate fails if source square is out of board bounds") {
    val state = GameState.initial
    val move = Move(Position(-1, 0), Position(0, 0))

    val result = GameService.validate(state, move)
    assert(result.isLeft)
    assert(result.left.get.contains("Square out of board bounds"))
  }

  test("validate fails if moving opponent's piece") {
    val state = GameState.initial
    val move = Move(Position(4, 6), Position(4, 5)) // Black pawn on White's turn

    val result = GameService.validate(state, move)
    assert(result.isLeft)
    assert(result.left.get.contains("It is White's turn, not Black's"))
  }

  test("applyMove returns Right(newState) when valid") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 3)) // e2 -> e4

    val result = GameService.applyMove(state, move)
    assert(result.isRight)

    val newState = result.toOption.get
    assert(newState.board.pieceAt(Position(4, 3)).contains(Piece(White, Pawn)))
    assert(newState.currentTurn == Black)
    assert(newState.moveHistory.head == move)
  }

  test("applyMove returns Left(reason) when invalid") {
    val state = GameState.initial
    val move = Move(Position(4, 3), Position(4, 4)) // empty square

    val result = GameService.applyMove(state, move)
    assert(result.isLeft)
  }

  // --- isGameOver and winner -----------------------------------------------

  test("isGameOver returns false for initial position") {
    val state = GameState.initial
    assert(!GameService.isGameOver(state))
  }

  test("isGameOver returns true if a king is missing") {
    val board = Board.empty.copy(
      squares = Map(Position(4, 0) -> Piece(White, King))
    )
    val state = GameState(board, White, List.empty)
    assert(GameService.isGameOver(state))
  }

  test("winner returns the color of remaining king") {
    val board = Board.empty.copy(
      squares = Map(Position(4, 0) -> Piece(White, King))
    )
    val state = GameState(board, White, List.empty)

    assert(GameService.winner(state).contains(White))
  }

  test("winner returns None if both kings exist") {
    val state = GameState.initial
    assert(GameService.winner(state).isEmpty)
  }

  test("winner returns None if both kings are lost") {
    val board = Board.empty
    val state = GameState(board, White, List.empty)

    assert(GameService.winner(state).isEmpty)
  }

  // --- currentPlayerHasPieces ------------------------------------------------

  test("currentPlayerHasPieces returns true if player has pieces") {
    val state = GameState.initial
    assert(GameService.currentPlayerHasPieces(state))
  }

  test("currentPlayerHasPieces returns false if player has no pieces") {
    val board = Board.empty.copy(
      squares = Map(Position(0, 7) -> Piece(Black, King))
    )
    val state = GameState(board, White, List.empty)
    assert(!GameService.currentPlayerHasPieces(state))
  }

}