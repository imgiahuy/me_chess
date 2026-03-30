package chess.domain

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
}