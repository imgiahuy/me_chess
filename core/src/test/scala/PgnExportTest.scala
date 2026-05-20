import controller.GameController
import model.PositionState

object PgnExportTest {
  def main(args: Array[String]): Unit = {
    val controller = new GameController()
    
    // Create a game with player names
    val game = controller.create("Magnus Carlsen", "Hikaru Nakamura")
    println(s"Game created: ${game.whitePlayer.name} vs ${game.blackPlayer.name}")
    println(s"Creation date: ${game.creationDate}")
    
    // Make some moves
    val moves = List("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5")
    var currentState = game
    
    moves.foreach { moveStr =>
      controller.makeMove(currentState, moveStr) match {
        case Right(newState) =>
          currentState = newState
          println(s"Move $moveStr applied successfully")
        case Left(error) =>
          println(s"Failed to apply move $moveStr: $error")
      }
    }
    
    // Export to PGN
    controller.exportToPgn(
      currentState, 
      event = "Test Tournament", 
      site = "Online", 
      filename = "test_game.pgn"
    )
    
    println("\nPGN export completed! Check 'test_game.pgn' file.")
  }
}
