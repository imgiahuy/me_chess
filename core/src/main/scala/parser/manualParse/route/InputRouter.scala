package parser.manualParse.route

import fastparse.*
import fastparse.NoWhitespace.*
import parser.Input

object InputRouter {

  // -----------------------------
  // Public API
  // -----------------------------

  def route(input: String): Input = {
    val s = input.trim

    if (parse(s, fen(using _)).isSuccess) Input.Fen(s)
    else if (parse(s, uci(using _)).isSuccess) Input.Uci(s)
    else if (parse(s, pgn(using _)).isSuccess) Input.Pgn(s)

    else Input.InvalidInput(s)
  }

  // -----------------------------
  // UCI
  // e2e4
  // -----------------------------

  private def file[$: P]: P[Unit] =
    P(CharIn("a-hA-H"))

  private def rank[$: P]: P[Unit] =
    P(CharIn("1-8"))

  private def uci[$: P]: P[Unit] =
    P(file ~ rank ~ file ~ rank ~ End)

  // -----------------------------
  // PGN (simplified)
  // e2e4 e7e5 g1f3
  // -----------------------------

  private def pgnMove[$: P]: P[Unit] =
    P(file ~ rank ~ file ~ rank)

  private def pgn[$: P]: P[Unit] =
    P(pgnMove.rep(sep = " ") ~ End)

  // -----------------------------
  // FEN
  // rnbqkbnr/pppppppp/8/... w KQkq - 0 1
  // -----------------------------

  private def piece[$: P]: P[Unit] =
    P(CharIn("prnbqkPRNBQK"))

  private def digit[$: P]: P[Unit] =
    P(CharIn("1-8"))

  private def fenSquare[$: P]: P[Unit] =
    P(piece | digit)

  private def fenRow[$: P]: P[Unit] =
    P(fenSquare.rep(min = 1))

  private def board[$: P]: P[Unit] =
    P(fenRow.rep(exactly = 8, sep = "/"))

  private def turn[$: P]: P[Unit] =
    P(CharIn("wb"))

  private def castling[$: P]: P[Unit] =
    P(
      "-" |
        CharsWhileIn("KQkq", min = 1)
    )

  private def enPassant[$: P]: P[Unit] =
    P(
      "-" |
        (file ~ rank)
    )

  private def halfmove[$: P]: P[Unit] =
    P(CharsWhileIn("0-9", min = 1))

  private def fullmove[$: P]: P[Unit] =
    P(CharsWhileIn("0-9", min = 1))

  private def fen[$: P]: P[Unit] =
    P(
      board ~ " " ~
        turn ~ " " ~
        castling ~ " " ~
        enPassant ~ " " ~
        halfmove ~ " " ~
        fullmove ~
        End
    )
}