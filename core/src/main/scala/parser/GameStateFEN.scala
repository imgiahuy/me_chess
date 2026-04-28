package fen

import domain.engine.GameState
import domain.model.Move

object GameStateFEN {

  def save(state: GameState): String = {
    val fen = FEN.toFEN(state.board, state.currentTurn)
    val moves = state.moveHistory.map(_.toAlgebraic).mkString("\n")
    s"$fen\nMOVES:\n$moves"
  }

  def load(input: String): GameState = {
    val lines = input.linesIterator.toList
    if (lines.isEmpty) throw new Exception("Empty game file")

    val (board, turn) = FEN.fromFEN(lines.head)

    val moves =
      if (lines.length >= 2 && lines(1) == "MOVES:") {
        lines.drop(2).map(Move.fromAlgebraic).collect { case Some(m) => m }
      } else List.empty

    GameState(board, turn, moves)
  }
}