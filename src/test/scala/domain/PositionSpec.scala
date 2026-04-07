package chess.domain

import domain.model.Position
import org.scalatest.funsuite.AnyFunSuite

class PositionSpec extends AnyFunSuite {

  // --- isValid -------------------------------------------------------------

  test("isValid should return true for valid positions") {
    assert(Position(0, 0).isValid)
    assert(Position(7, 7).isValid)
    assert(Position(4, 3).isValid)
  }

  test("isValid should return false for invalid positions") {
    assert(!Position(-1, 0).isValid)
    assert(!Position(0, -1).isValid)
    assert(!Position(8, 0).isValid)
    assert(!Position(0, 8).isValid)
  }

  // --- toAlgebraic ---------------------------------------------------------

  test("toAlgebraic should convert position correctly") {
    assert(Position(0, 0).toAlgebraic == "a1")
    assert(Position(4, 1).toAlgebraic == "e2")
    assert(Position(7, 7).toAlgebraic == "h8")
  }

  // --- fromAlgebraic -------------------------------------------------------

  test("fromAlgebraic should parse valid lowercase input") {
    assert(Position.fromAlgebraic("a1").contains(Position(0, 0)))
    assert(Position.fromAlgebraic("e2").contains(Position(4, 1)))
    assert(Position.fromAlgebraic("h8").contains(Position(7, 7)))
  }

  test("fromAlgebraic should parse valid uppercase input") {
    assert(Position.fromAlgebraic("A1").contains(Position(0, 0)))
    assert(Position.fromAlgebraic("E2").contains(Position(4, 1)))
  }

  test("fromAlgebraic should return None for invalid strings") {
    assert(Position.fromAlgebraic("").isEmpty)
    assert(Position.fromAlgebraic("a").isEmpty)
    assert(Position.fromAlgebraic("a10").isEmpty)
    assert(Position.fromAlgebraic("i1").isEmpty)   // invalid file
    assert(Position.fromAlgebraic("a9").isEmpty)   // invalid rank
    assert(Position.fromAlgebraic("11").isEmpty)   // not a square
  }

  // --- round-trip property -------------------------------------------------

  test("toAlgebraic and fromAlgebraic should round-trip") {
    val positions =
      for {
        col <- 0 until 8
        row <- 0 until 8
      } yield Position(col, row)

    positions.foreach { pos =>
      val parsed = Position.fromAlgebraic(pos.toAlgebraic)
      assert(parsed.contains(pos))
    }
  }

}