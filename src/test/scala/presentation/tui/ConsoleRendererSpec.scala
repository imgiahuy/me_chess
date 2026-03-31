package chess.presentation.tui

import org.scalatest.funsuite.AnyFunSuite
import chess.domain._
import chess.application.GameService

class ConsoleRendererSpec extends AnyFunSuite {

  // --- renderBoard ---------------------------------------------------------

  test("renderBoard prints initial board with pieces") {
    val board = Board.initial
    val output = ConsoleRenderer.renderBoard(board)

    // Check header and some known pieces
    assert(output.contains("  a b c d e f g h"))
    assert(output.contains("1 R N B Q K B N R")) // White back rank at bottom
    assert(output.contains("8 r n b q k b n r")) // Black back rank at top
  }

  test("renderBoard uses LightEmpty and DarkEmpty for empty squares") {
    val emptyBoard = Board.empty
    val output = ConsoleRenderer.renderBoard(emptyBoard)

    // Should contain only empty squares symbols
    assert(output.exists(c => c == ' ' || c == '.'))
  }

  // --- renderRank ----------------------------------------------------------

  test("renderRank prints one rank correctly") {
    val board = Board.empty.copy(
      squares = Map(Position(0, 0) -> Piece(White, Rook))
    )
    val rank0 = ConsoleRenderer.renderRank(board)(0)
    assert(rank0.startsWith("1 "))
    assert(rank0.contains("R"))
  }

  // --- renderGameState -----------------------------------------------------

  test("renderGameState includes board, turn, moves, last move") {
    val state = GameState.initial
    val output = ConsoleRenderer.renderGameState(state)
    assert(output.contains("Turn: White"))
    assert(output.contains("Moves played: 0"))
    assert(output.contains("No moves yet"))

    val move = Move(Position(4, 1), Position(4, 3)) // e2e4
    val newState = state.applyMove(move)
    val newOutput = ConsoleRenderer.renderGameState(newState)
    assert(newOutput.contains("Last move: e2e4"))
    assert(newOutput.contains("Moves played: 1"))
  }

  // --- renderOutcome -------------------------------------------------------

  test("renderOutcome displays winner correctly") {
    val board = Board.empty.copy(
      squares = Map(Position(4, 0) -> Piece(White, King))
    )
    val state = GameState(board, White, List.empty)
    val output = ConsoleRenderer.renderOutcome(state)
    assert(output.contains("White wins"))
  }

  test("renderOutcome displays draw if no kings") {
    val board = Board.empty
    val state = GameState(board, White, List.empty)
    val output = ConsoleRenderer.renderOutcome(state)
    assert(output.contains("draw"))
  }

  // --- renderError ---------------------------------------------------------

  test("renderError formats error messages") {
    val msg = "Invalid move"
    val output = ConsoleRenderer.renderError(msg)
    assert(output.contains(msg))
    assert(output.startsWith("Error:"))
  }

  // --- renderWinner / renderDraw -------------------------------------------

  test("renderWinner and renderDraw return correct strings") {
    assert(ConsoleRenderer.renderWinner(White).contains("White wins"))
    assert(ConsoleRenderer.renderWinner(Black).contains("Black wins"))
    assert(ConsoleRenderer.renderDraw.contains("draw"))
  }

  // --- renderHelp ----------------------------------------------------------

  test("renderHelp contains expected keywords") {
    val help = ConsoleRenderer.renderHelp
    assert(help.contains("Scala Chess"))
    assert(help.contains("<move>"))
    assert(help.contains("board"))
    assert(help.contains("help"))
    assert(help.contains("quit"))
    assert(help.contains("uppercase=White"))
    assert(help.contains("lowercase=Black"))
  }

}