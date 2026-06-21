package tournament.engine

import tournament.model.Participant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PairingEngineSpec extends AnyFunSuite with Matchers {

  test("Single elimination pairing generates pairings") {
    val participants = List(
      Participant("p1", "Player 1", None),
      Participant("p2", "Player 2", None),
      Participant("p3", "Player 3", None),
      Participant("p4", "Player 4", None)
    )
    
    val pairings = PairingEngine.singleElimination(participants, 1)
    
    pairings should not be empty
    pairings.foreach(p => p.round shouldEqual 1)
  }

  test("Single elimination handles odd number of participants") {
    val participants = List(
      Participant("p1", "Player 1", None),
      Participant("p2", "Player 2", None),
      Participant("p3", "Player 3", None)
    )
    
    val pairings = PairingEngine.singleElimination(participants, 1)
    
    pairings should not be empty
  }

  test("Double elimination pairing generates winners and losers brackets") {
    val participants = List(
      Participant("p1", "Player 1", None),
      Participant("p2", "Player 2", None),
      Participant("p3", "Player 3", None),
      Participant("p4", "Player 4", None)
    )
    
    val pairings = PairingEngine.doubleElimination(
      participants,
      1,
      winnersBracket = List("p1", "p2"),
      losersBracket = List("p3", "p4")
    )
    
    pairings should not be empty
  }

  test("Group stage pairing divides participants into groups") {
    val participants = (1 to 8).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.groupStage(participants, 1, groupSize = 4)
    
    pairings should not be empty
  }

  test("Group stage pairing handles incomplete groups") {
    val participants = (1 to 5).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.groupStage(participants, 1, groupSize = 4)
    
    pairings should not be empty
  }

  test("League pairing returns round-robin for specific round") {
    val participants = (1 to 4).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.league(participants, 1, gamesPerPairing = 1)
    
    pairings should not be empty
  }

  test("Random knockout pairing generates random pairs") {
    val participants = (1 to 4).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.randomKnockout(participants, 1)
    
    pairings should not be empty
  }

  test("Random knockout handles odd number of participants") {
    val participants = (1 to 3).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.randomKnockout(participants, 1)
    
    pairings should not be empty
  }

  test("Random knockout returns empty for single participant") {
    val participants = List(Participant("p1", "Player 1", None))
    
    val pairings = PairingEngine.randomKnockout(participants, 1)
    
    pairings shouldBe empty
  }

  test("Round robin still works with existing implementation") {
    val participants = (1 to 4).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val rounds = PairingEngine.roundRobin(participants, gamesPerPairing = 1)
    
    rounds should not be empty
  }

  test("Swiss pairing still works with existing implementation") {
    val participants = (1 to 4).map(i => Participant(s"p$i", s"Player $i", None)).toList
    
    val pairings = PairingEngine.swiss(participants, 1, Set.empty)
    
    pairings should not be empty
  }

  test("Arena pairing still works with existing implementation") {
    val participants = (1 to 4).map(i => Participant(s"p$i", s"Player $i", None)).toList
    val standings = Map("p1" -> 1.0, "p2" -> 0.5, "p3" -> 0.5, "p4" -> 0.0)
    
    val pairings = PairingEngine.arena(participants, 1, standings)
    
    pairings should not be empty
  }
}
