package model

/** Represents draw conditions in chess */
sealed trait DrawReason

case object Stalemate extends DrawReason
case object InsufficientMaterial extends DrawReason
case object ThreefoldRepetition extends DrawReason
case object FiftyMoveRule extends DrawReason
case object MutualAgreement extends DrawReason
case object DeadPosition extends DrawReason

