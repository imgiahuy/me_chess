package parser

enum Input:
  
  case Fen(value: String)
  case Uci(value: String)
  case Pgn(value: String)
  case ModelSnap(snap: model.Snapshot)
  case ModelMove(move: model.Move)
  case ListMoves(snap: model.Snapshot)
  case InvalidInput(value: String)