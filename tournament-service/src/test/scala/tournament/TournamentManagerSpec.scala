package tournament

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tournament.engine.TournamentManager
import tournament.engine.PairingEngine
import tournament.model._

class TournamentManagerSpec extends AnyFlatSpec with Matchers:

  private val manager = new TournamentManager()

  "TournamentManager" should "create a tournament with created status" in {
    val config = TournamentConfig("Test", TournamentFormat.RoundRobin, rounds = 1)
    val t = Tournament.create(config)
    t.status shouldBe TournamentStatus.Created
    t.participants shouldBe empty
  }

  it should "register participants and open the tournament" in {
    val config = TournamentConfig("Test", TournamentFormat.RoundRobin, rounds = 1)
    val t = Tournament.create(config)
    val p1 = Participant("p1", "Alice")
    val p2 = Participant("p2", "Bob")

    val afterP1 = manager.register(t, p1).getOrElse(fail())
    afterP1.status shouldBe TournamentStatus.Open
    afterP1.participants should have size 1

    val afterP2 = manager.register(afterP1, p2).getOrElse(fail())
    afterP2.participants should have size 2
  }

  it should "start a round-robin tournament and generate all pairings" in {
    val config = TournamentConfig("RR", TournamentFormat.RoundRobin, rounds = 1, gamesPerPairing = 1)
    val t = Tournament.create(config)
    val afterReg = (for {
      a <- manager.register(t, Participant("p1", "Alice"))
      b <- manager.register(a, Participant("p2", "Bob"))
      c <- manager.register(b, Participant("p3", "Carol"))
    } yield c).getOrElse(fail())

    val started = manager.start(afterReg).getOrElse(fail())
    started.status shouldBe TournamentStatus.Running
    started.roundsData should have size 1
    started.roundsData.head.pairings should have size 3
  }

  it should "calculate standings after results" in {
    val config = TournamentConfig("RR", TournamentFormat.RoundRobin, rounds = 1, gamesPerPairing = 1)
    val t = Tournament.create(config)
    val afterReg = (for {
      a <- manager.register(t, Participant("p1", "Alice"))
      b <- manager.register(a, Participant("p2", "Bob"))
    } yield b).getOrElse(fail())

    val started = manager.start(afterReg).getOrElse(fail())
    val pairing = started.roundsData.head.pairings.head
    val gameId = s"${pairing.whiteId}-${pairing.blackId}-${pairing.round}"
    val afterResult = manager.reportResult(started, gameId, GameResult.WhiteWin).getOrElse(fail())

    afterResult.standings should have size 2
    val winner = afterResult.standings.find(_.participantId == pairing.whiteId).getOrElse(fail())
    winner.score shouldBe 1.0
    winner.wins shouldBe 1
  }

  "PairingEngine.roundRobin" should "generate double round-robin with color swap" in {
    val participants = List(Participant("a", "A"), Participant("b", "B"))
    val rounds = PairingEngine.roundRobin(participants, gamesPerPairing = 2)
    rounds should have size 2
    rounds(0).pairings.head.whiteId shouldBe "a"
    rounds(1).pairings.head.whiteId shouldBe "b"
  }

  "PairingEngine.swiss" should "pair top vs bottom" in {
    val participants = List(
      Participant("p1", "A"),
      Participant("p2", "B"),
      Participant("p3", "C"),
      Participant("p4", "D")
    )
    val pairings = PairingEngine.swiss(participants, 1, Set.empty)
    pairings should have size 2
    pairings.head.whiteId shouldBe "p1"
    pairings.head.blackId shouldBe "p3"
  }
