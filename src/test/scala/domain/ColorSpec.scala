package chess.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ColorSpec extends AnyFlatSpec with Matchers {

  "White" should "have Black as its opposite" in {
    White.opposite shouldBe Black
  }

  it should "display as 'White'" in {
    White.toString shouldBe "White"
  }

  "Black" should "have White as its opposite" in {
    Black.opposite shouldBe White
  }

  it should "display as 'Black'" in {
    Black.toString shouldBe "Black"
  }

  "opposite" should "be an involution (applying twice returns the original)" in {
    White.opposite.opposite shouldBe White
    Black.opposite.opposite shouldBe Black
  }
}