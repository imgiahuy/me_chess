package chess.domain

import org.scalatest.funsuite.AnyFunSuite

class MoveSpec extends AnyFunSuite {

  test("toAlgebraic should concatenate from and to positions") {
    val move = Move(Position(4, 1), Position(4, 3)) // e2 -> e4
    assert(move.toAlgebraic == "e2e4")
  }

  test("toAlgebraic should work for edge squares") {
    val move = Move(Position(0, 0), Position(7, 7)) // a1 -> h8
    assert(move.toAlgebraic == "a1h8")
  }

  test("moves with same from and to should still serialize correctly") {
    val move = Move(Position(3, 3), Position(3, 3)) // d4 -> d4
    assert(move.toAlgebraic == "d4d4")
  }

  test("moves with same positions should be equal") {
    val m1 = Move(Position(4, 1), Position(4, 3))
    val m2 = Move(Position(4, 1), Position(4, 3))

    assert(m1 == m2)
  }

  test("moves with different positions should not be equal") {
    val m1 = Move(Position(4, 1), Position(4, 3))
    val m2 = Move(Position(4, 1), Position(4, 2))
    val m3 = Move(Position(3, 1), Position(3, 3))

    assert(m1 != m2)
    assert(m1 != m3)
  }

  test("toAlgebraic should be consistent with Position conversion") {
    val from = Position(2, 1) // c2
    val to   = Position(2, 3) // c4

    val move = Move(from, to)

    assert(move.toAlgebraic == from.toAlgebraic + to.toAlgebraic)
  }

}