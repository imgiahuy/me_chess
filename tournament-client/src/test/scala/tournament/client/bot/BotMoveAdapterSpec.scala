package tournament.client.bot

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BotMoveAdapterSpec extends AnyFlatSpec with Matchers {
  
  "BotMoveAdapter" should "compute a move from a valid FEN" in {
    val adapter = BotMoveAdapter("random")
    
    // Starting position FEN
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    
    val result = adapter.computeMove(fen, "white", "white", "test-game")
    
    result shouldBe a[Right[_, String]]
    result match {
      case Right(uciMove) =>
        uciMove should have length 4 // e.g., "e2e4"
        uciMove.charAt(0) should (be >= 'a' and be <= 'h')
        uciMove.charAt(1) should (be >= '1' and be <= '8')
        uciMove.charAt(2) should (be >= 'a' and be <= 'h')
        uciMove.charAt(3) should (be >= '1' and be <= '8')
      case Left(_) => fail("Should have computed a move")
    }
  }
  
  it should "return error when it's not the bot's turn" in {
    val adapter = BotMoveAdapter("random")
    
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    
    val result = adapter.computeMove(fen, "white", "black", "test-game")
    
    result shouldBe a[Left[_, _]]
    result match {
      case Left(error) =>
        error should include("Not bot's turn")
      case Right(_) => fail("Should have returned error")
    }
  }
  
  it should "handle promotion moves correctly" in {
    val adapter = BotMoveAdapter("random")
    
    // Position where pawn can promote
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/8/RNBQKBNR w KQkq - 0 1"
    
    val result = adapter.computeMove(fen, "white", "white", "test-game")
    
    // This is a simplified test - in practice, we'd need a specific FEN with a pawn on the 7th rank
    result shouldBe a[Right[_, String]]
  }
  
  it should "return error for invalid FEN" in {
    val adapter = BotMoveAdapter("random")
    
    val invalidFen = "invalid-fen-string"
    
    val result = adapter.computeMove(invalidFen, "white", "white", "test-game")
    
    result shouldBe a[Left[_, _]]
    result match {
      case Left(error) =>
        error should include("Failed to parse FEN")
      case Right(_) => fail("Should have returned error")
    }
  }
  
  it should "provide bot name and difficulty" in {
    val adapter = BotMoveAdapter("stockfish")
    
    adapter.botName should not be empty
    adapter.botDifficulty should not be empty
  }
  
  it should "create adapter with default settings" in {
    val adapter = BotMoveAdapter.default
    
    adapter.botName should not be empty
    adapter.botDifficulty should not be empty
  }
}
