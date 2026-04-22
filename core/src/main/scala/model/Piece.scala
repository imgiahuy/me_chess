package domain.model

/** An immutable chess piece: a colour + a type.
 *
 * `symbol` is derived functionally — uppercase for White, lowercase for Black.
 * This follows standard FEN / console display conventions.
 */
case class Piece(color: Color, pieceType: PieceType) {

  /** Single-character display symbol following FEN conventions. */
  val symbol: Char = {
    val base = pieceType match {
      case King   => 'K'
      case Queen  => 'Q'
      case Rook   => 'R'
      case Bishop => 'B'
      case Knight => 'N'
      case Pawn   => 'P'
    }
    if (color == White) base else base.toLower
  }

  override def toString: String = s"${color} ${pieceType}"
}