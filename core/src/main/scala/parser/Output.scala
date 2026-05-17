package parser

import model.{Board, Color}

enum Output:
  case fenToModel(board: Board, color: Color)
  case pgnToModel(moves: List[model.Move])
  case uciToModel(move: model.Move)
  case InvalidOutput(reason: String)
