package model

/** Represents special move types */
sealed trait SpecialMoveType
case object CastlingKingSide extends SpecialMoveType
case object CastlingQueenSide extends SpecialMoveType
case object EnPassant extends SpecialMoveType
case class Promotion(promoteTo: PieceType) extends SpecialMoveType

/** An immutable description of one player's move: a source and destination square.
 *
 * This is a plain data carrier with no validation — validation belongs in
 * the logic layer (GameEngine).
 */
case class Move(from: Position, to: Position, specialMove: Option[SpecialMoveType] = None)
