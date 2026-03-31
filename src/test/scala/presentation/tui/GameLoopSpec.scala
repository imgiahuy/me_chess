package chess.presentation.tui

import org.scalatest.funsuite.AnyFunSuite
import chess.domain._
import chess.application.{GameService, MoveParser}

class GameLoopSpec extends AnyFunSuite {

  // --- parseCommand --------------------------------------------------------

  test("parseCommand should map keywords to commands") {
    assert(GameLoop.parseCommand("board") == GameLoop.ShowBoard)
    assert(GameLoop.parseCommand("help") == GameLoop.ShowHelp)
    assert(GameLoop.parseCommand("quit") == GameLoop.Quit)
  }

  test("parseCommand should be case-insensitive and trim whitespace") {
    assert(GameLoop.parseCommand("  BOARD  ") == GameLoop.ShowBoard)
    assert(GameLoop.parseCommand("HeLp") == GameLoop.ShowHelp)
    assert(GameLoop.parseCommand("QuIt") == GameLoop.Quit)
  }

  test("parseCommand returns MakeMove for unknown input") {
    val raw = "e2e4"
    GameLoop.parseCommand(raw) match {
      case GameLoop.MakeMove(moveStr) => assert(moveStr == raw)
      case _ => fail("Expected MakeMove")
    }
  }

  // --- processCommand ------------------------------------------------------

  test("processCommand should handle ShowBoard") {
    val state = GameState.initial
    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.ShowBoard)

    assert(newState == state)
    assert(msg.exists(_.contains("♜")) || msg.exists(_.contains("R"))) // board rendered
  }

  test("processCommand should handle ShowHelp") {
    val state = GameState.initial
    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.ShowHelp)

    assert(newState == state)
    assert(msg.exists(_.toLowerCase.contains("help")))
  }

  test("processCommand should handle Quit") {
    val state = GameState.initial
    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.Quit)

    assert(newState == state)
    assert(msg.contains("Goodbye!"))
  }

  test("processCommand should handle valid MakeMove") {
    val state = GameState.initial
    val raw = "e2e4"
    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.MakeMove(raw))

    assert(newState.board.pieceAt(Position(4, 3)).contains(Piece(White, Pawn)))
    assert(msg.exists(_.contains("Moved: e2e4")))
  }

  test("processCommand should handle invalid MakeMove") {
    val state = GameState.initial
    val raw = "e4e5" // empty square

    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.MakeMove(raw))

    assert(newState == state) // state unchanged
    assert(msg.exists(_.toLowerCase.contains("no piece at e4"))) // match the actual error
  }

  // --- loop ---------------------------------------------------------------

  test("loop terminates on EOF and writes Goodbye") {
    val outputs = scala.collection.mutable.Buffer.empty[String]
    val inputs = Iterator.empty[String]

    GameLoop.loop(
      state = GameState.initial,
      readLine = () => if (inputs.hasNext) Some(inputs.next()) else None,
      writeLine = s => outputs.append(s)
    )

    assert(outputs.exists(_.toLowerCase.contains("goodbye")))
  }

  test("loop handles a sequence of commands including moves and quit") {
    val outputs = scala.collection.mutable.Buffer.empty[String]
    val commands = Iterator(
      "board",
      "e2e4",
      "help",
      "quit"
    )

    GameLoop.loop(
      state = GameState.initial,
      readLine = () => if (commands.hasNext) Some(commands.next()) else None,
      writeLine = s => outputs.append(s)
    )

    // Expect at least one board render, one help render, and goodbye
    assert(outputs.exists(_.toLowerCase.contains("help")))
    assert(outputs.exists(_.toLowerCase.contains("goodbye")))
    assert(outputs.exists(_.contains("Moved: e2e4")))
  }

}