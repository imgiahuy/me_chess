package model

/** An immutable description of one player's move: a source and destination square.
 *
 * This is a plain data carrier with no validation — validation belongs in
 * the logic layer (GameEngine).
 */
case class Move(from: Position, to: Position)