package chess.domain

import org.scalatest.funsuite.AnyFunSuite

class GameStateSpec extends AnyFunSuite {

  test("initial game state has correct board, turn, and empty history") {
    val state = GameState.initial

    assert(state.currentTurn == White)
    assert(state.moveHistory.isEmpty)
    assert(state.board.allPieces.size == 32)
  }

  test("applyMove updates board correctly") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 3)) // e2 -> e4
    val newState = state.applyMove(move)

    assert(newState.board.pieceAt(Position(4, 3)).contains(Piece(White, Pawn)))
    assert(newState.board.isEmpty(Position(4, 1)))
  }

  test("applyMove advances turn to opposite color") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 3)) // e2 -> e4
    val newState = state.applyMove(move)

    assert(newState.currentTurn == Black)
  }

  test("applyMove prepends move to moveHistory") {
    val state = GameState.initial
    val move1 = Move(Position(4, 1), Position(4, 3))
    val move2 = Move(Position(3, 1), Position(3, 3))

    val state1 = state.applyMove(move1)
    val state2 = state1.applyMove(move2)

    assert(state2.moveHistory.head == move2)
    assert(state2.moveHistory.tail.head == move1)
  }

  test("applyMove does not mutate original GameState") {
    val state = GameState.initial
    val move = Move(Position(4, 1), Position(4, 3))
    val newState = state.applyMove(move)

    // original state still has piece at e2
    assert(state.board.pieceAt(Position(4, 1)).contains(Piece(White, Pawn)))
    assert(state.currentTurn == White)
    assert(state.moveHistory.isEmpty)
  }

  test("lastMove returns most recent move or None") {
    val state = GameState.initial
    assert(state.lastMove.isEmpty)

    val move = Move(Position(4, 1), Position(4, 3))
    val newState = state.applyMove(move)

    assert(newState.lastMove.contains(move))
  }

  test("totalMoves returns correct number of half-moves") {
    val state = GameState.initial
    assert(state.totalMoves == 0)

    val move1 = Move(Position(4, 1), Position(4, 3))
    val move2 = Move(Position(3, 1), Position(3, 3))

    val state1 = state.applyMove(move1)
    val state2 = state1.applyMove(move2)

    assert(state1.totalMoves == 1)
    assert(state2.totalMoves == 2)
  }

  test("applyMove can handle captures") {
    val customBoard = Board.empty.copy(
      squares = Map(
        Position(0, 0) -> Piece(White, Rook),
        Position(0, 1) -> Piece(Black, Pawn)
      )
    )

    val state = GameState(customBoard, White, List.empty)
    val move = Move(Position(0, 0), Position(0, 1))

    val newState = state.applyMove(move)

    assert(newState.board.pieceAt(Position(0, 1)).contains(Piece(White, Rook)))
    assert(newState.board.pieceAt(Position(0, 0)).isEmpty)
    assert(newState.currentTurn == Black)
  }

}