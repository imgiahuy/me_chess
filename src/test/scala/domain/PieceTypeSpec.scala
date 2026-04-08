package chess.domain

import domain.model.{Bishop, King, Knight, Pawn, PieceType, Queen, Rook}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PieceTypeSpec extends AnyFlatSpec with Matchers {

  "PieceType.all" should "contain exactly the six standard piece types" in {
    PieceType.all should contain theSameElementsAs Seq(King, Queen, Rook, Bishop, Knight, Pawn)
  }

  it should "have six elements" in {
    PieceType.all should have size 6
  }

  "Each PieceType" should "be a distinct value" in {
    // Exercises all six case objects so they all appear in coverage
    val types: Set[PieceType] = Set(King, Queen, Rook, Bishop, Knight, Pawn)
    types should have size 6
  }
}