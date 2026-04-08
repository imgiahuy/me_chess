package chess.application

import org.scalatest.funsuite.AnyFunSuite
import domain.model.{Move, Position}
import presentation.parser.MoveParser

class MoveParserSpec extends AnyFunSuite {

  // --- parse valid moves ---------------------------------------------------

  test("parse should handle compact format without separators") {
    val input = "e2e4"
    val expected = Move(Position(4, 1), Position(4, 3))

    assert(MoveParser.parse(input).contains(expected))
  }

  test("parse should handle space-separated format") {
    val input = "e2 e4"
    val expected = Move(Position(4, 1), Position(4, 3))

    assert(MoveParser.parse(input).contains(expected))
  }

  test("parse should handle hyphen-separated format") {
    val input = "e2-e4"
    val expected = Move(Position(4, 1), Position(4, 3))

    assert(MoveParser.parse(input).contains(expected))
  }

  test("parse should be case-insensitive") {
    val input1 = "E2E4"
    val input2 = "E2 e4"
    val input3 = "E2-E4"

    val expected = Move(Position(4, 1), Position(4, 3))

    assert(MoveParser.parse(input1).contains(expected))
    assert(MoveParser.parse(input2).contains(expected))
    assert(MoveParser.parse(input3).contains(expected))
  }

  // --- parse invalid moves -------------------------------------------------

  test("parse should return None for invalid square strings") {
    assert(MoveParser.parse("i9e4").isEmpty)  // invalid from
    assert(MoveParser.parse("e2i9").isEmpty)  // invalid to
    assert(MoveParser.parse("abcd").isEmpty)  // nonsensical
    assert(MoveParser.parse("").isEmpty)      // empty string
    assert(MoveParser.parse("e2e").isEmpty)   // too short
    assert(MoveParser.parse("e2e4e5").isEmpty) // too long
  }

  test("parse should trim whitespace") {
    val input = "  e2 e4  "
    val expected = Move(Position(4, 1), Position(4, 3))
    assert(MoveParser.parse(input).contains(expected))
  }

  test("parse should handle multiple whitespace or hyphen separators") {
    val input1 = "e2   e4"
    val input2 = "e2--e4"
    val input3 = "e2 -  e4"

    val expected = Move(Position(4, 1), Position(4, 3))

    assert(MoveParser.parse(input1).contains(expected))
    assert(MoveParser.parse(input2).contains(expected))
    assert(MoveParser.parse(input3).contains(expected))
  }

  // --- parsePosition delegates to Position.fromAlgebraic -------------------

  test("parsePosition should parse valid square") {
    val input = "e2"
    val expected = Position(4, 1)

    assert(MoveParser.parsePosition(input).contains(expected))
  }

  test("parsePosition should return None for invalid square") {
    assert(MoveParser.parsePosition("i9").isEmpty)
    assert(MoveParser.parsePosition("z0").isEmpty)
  }

}