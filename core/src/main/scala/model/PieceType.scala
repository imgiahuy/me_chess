package model

/** The six chess piece types.
 *
 * Sealed trait ensures exhaustive pattern matching everywhere a PieceType
 * is deconstructed.
 */
sealed trait PieceType
case object King   extends PieceType
case object Queen  extends PieceType
case object Rook   extends PieceType
case object Bishop extends PieceType
case object Knight extends PieceType
case object Pawn   extends PieceType

object PieceType {
  val all: Seq[PieceType] = Seq(King, Queen, Rook, Bishop, Knight, Pawn)

  def toAbbrev(pt: PieceType): Char = pt match {
    case King => 'K'
    case Queen => 'Q'
    case Rook => 'R'
    case Bishop => 'B'
    case Knight => 'N'
    case Pawn => 'P'
  }

  def fromAbbrev(c: Char): PieceType = c match {
    case 'K' => King
    case 'Q' => Queen
    case 'R' => Rook
    case 'B' => Bishop
    case 'N' => Knight
    case 'P' => Pawn
    case _ => throw new IllegalArgumentException(s"Invalid piece type: $c")
  }
}

