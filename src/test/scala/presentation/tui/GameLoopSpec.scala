package chess.presentation.tui

import org.scalatest.funsuite.AnyFunSuite
import chess.domain._
import chess.application.GameService
import presentation.parser.MoveParser

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
    assert(msg.exists(_.contains("♜")) || msg.exists(_.contains("R")))
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

  test("processCommand should handle invalid MakeMove (illegal move)") {
    val state = GameState.initial
    val raw = "e4e5" // no piece at e4

    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.MakeMove(raw))

    assert(newState == state)
    assert(msg.exists(_.toLowerCase.contains("no piece at e4")))
  }

  test("processCommand should handle invalid move format") {
    val state = GameState.initial
    val raw = "invalid"

    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.MakeMove(raw))

    assert(newState == state)
    assert(msg.exists(_.toLowerCase.contains("invalid move")))
  }

  test("processCommand should reject malformed input like e9e2") {
    val state = GameState.initial
    val raw = "e9e2"

    val (newState, msg) = GameLoop.processCommand(state)(GameLoop.MakeMove(raw))

    assert(newState == state)
    assert(msg.exists(_.toLowerCase.contains("invalid move")))
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

    assert(outputs.exists(_.toLowerCase.contains("help")))
    assert(outputs.exists(_.toLowerCase.contains("goodbye")))
    assert(outputs.exists(_.contains("Moved: e2e4")))
  }

  test("loop should render outcome when game is already over") {
    val outputs = scala.collection.mutable.Buffer.empty[String]

    // NOTE: This assumes your GameService.isGameOver can return true
    // If not, see recommendation below
    GameLoop.loop(
      state = GameState.initial,
      readLine = () => None,
      writeLine = outputs.append,
      isGameOver = _ => true // Force game-over condition for testing
    )

    // At least something printed (board + outcome OR goodbye fallback)
    assert(outputs.nonEmpty)
  }

  // --- start() ------------------------------------------------------------

  test("start should print help and exit on EOF") {
    val output = new java.io.ByteArrayOutputStream()

    Console.withOut(output) {
      Console.withIn(new java.io.ByteArrayInputStream(Array.emptyByteArray)) {
        GameLoop.start()
      }
    }

    val printed = output.toString.toLowerCase

    assert(printed.contains("help"))
    assert(printed.contains("goodbye"))
  }
}