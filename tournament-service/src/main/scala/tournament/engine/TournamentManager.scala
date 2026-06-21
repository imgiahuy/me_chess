package tournament.engine

import tournament.model._

import java.time.Instant
import scala.util.{Try, Success, Failure}

/** Business logic for tournament lifecycle: registration, start, pairings, results, standings. */
class TournamentManager:

  def register(tournament: Tournament, participant: Participant): Either[String, Tournament] =
    if tournament.status != TournamentStatus.Created && tournament.status != TournamentStatus.Open then
      Left("Cannot register participants after the tournament has started.")
    else if tournament.participants.exists(_.id == participant.id) then
      Left("Participant already registered.")
    else
      val updated = tournament.copy(
        participants = tournament.participants :+ participant,
        status = TournamentStatus.Open
      )
      Right(updated)

  def start(tournament: Tournament): Either[String, Tournament] =
    if tournament.status != TournamentStatus.Created && tournament.status != TournamentStatus.Open then
      Left("Tournament cannot be started from status: " + tournament.status)
    else if tournament.participants.size < 2 then
      Left("At least two participants are required.")
    else
      val initialPairings = tournament.format match
        case TournamentFormat.RoundRobin =>
          PairingEngine.roundRobin(tournament.participants, tournament.gamesPerPairing)
        case TournamentFormat.Swiss | TournamentFormat.Arena =>
          List(Round(number = 1, pairings = generatePairingsForRound(tournament, 1)))
      val rounds = if tournament.format == TournamentFormat.Arena then List.empty else initialPairings
      val updated = tournament.copy(
        status = TournamentStatus.Running,
        startedAt = Some(Instant.now()),
        roundsData = if rounds.isEmpty then List(Round(1, generatePairingsForRound(tournament, 1), Some(Instant.now()))) else rounds.map(_.copy(startedAt = Some(Instant.now()))),
        standings = tournament.participants.map(p => Standing(p.id, p.name, rating = p.initialRating))
      )
      Right(updated)

  def reportResult(tournament: Tournament, gameId: String, result: GameResult): Either[String, Tournament] =
    if tournament.status != TournamentStatus.Running then
      Left("Tournament is not running.")
    else
      val updatedRounds = tournament.roundsData.map { round =>
        val updatedPairings = round.pairings.map {
          case p if p.gameId.contains(gameId) => p.copy(result = Some(result))
          case p => p
        }
        if round.pairings.exists(p => p.gameId.contains(gameId) && p.result.isEmpty) then
          round.copy(pairings = updatedPairings, finishedAt = if allPairingsFinished(updatedPairings) then Some(Instant.now()) else round.finishedAt)
        else round.copy(pairings = updatedPairings)
      }

      val updatedStandings = recalculateStandings(tournament.participants, updatedRounds)
      val updated = tournament.copy(roundsData = updatedRounds, standings = updatedStandings)

      // Auto-generate next round for Swiss/Arena if current round finished
      val withNextRound = maybeAdvanceRound(updated)
      Right(withNextRound)

  def finish(tournament: Tournament): Either[String, Tournament] =
    if tournament.status != TournamentStatus.Running then
      Left("Only running tournaments can be finished.")
    else
      Right(tournament.copy(
        status = TournamentStatus.Finished,
        finishedAt = Some(Instant.now()),
        standings = recalculateStandings(tournament.participants, tournament.roundsData)
      ))

  def assignGameId(tournament: Tournament, roundNumber: Int, whiteId: String, blackId: String, gameId: String): Either[String, Tournament] =
    val updated = tournament.roundsData.map { round =>
      if round.number == roundNumber then
        val updatedPairings = round.pairings.map {
          case p if p.whiteId == whiteId && p.blackId == blackId => p.copy(gameId = Some(gameId))
          case p => p
        }
        round.copy(pairings = updatedPairings)
      else round
    }
    Right(tournament.copy(roundsData = updated))

  private def generatePairingsForRound(tournament: Tournament, roundNumber: Int): List[Pairing] =
    tournament.format match
      case TournamentFormat.Swiss =>
        val previous = previousPairings(tournament.roundsData)
        PairingEngine.swiss(tournament.participants, roundNumber, previous)
      case TournamentFormat.Arena =>
        val standings = tournament.standings.map(s => s.participantId -> s.score).toMap
        PairingEngine.arena(tournament.participants, roundNumber, standings)
      case TournamentFormat.RoundRobin =>
        Nil

  private def previousPairings(rounds: List[Round]): Set[(String, String)] =
    rounds.flatMap(_.pairings.map(p => (p.whiteId, p.blackId))).toSet

  private def allPairingsFinished(pairings: List[Pairing]): Boolean =
    pairings.forall(_.result.isDefined)

  private def recalculateStandings(participants: List[Participant], rounds: List[Round]): List[Standing] =
    val base = participants.map(p => p.id -> Standing(p.id, p.name, rating = p.initialRating)).toMap
    val updated = rounds.flatMap(_.pairings).foldLeft(base) { (map, pairing) =>
      pairing.result match
        case None => map
        case Some(GameResult.WhiteWin) =>
          val w = map(pairing.whiteId)
          val b = map(pairing.blackId)
          map.updated(pairing.whiteId, w.copy(played = w.played + 1, wins = w.wins + 1, score = w.score + 1.0))
            .updated(pairing.blackId, b.copy(played = b.played + 1, losses = b.losses + 1, score = b.score + 0.0))
        case Some(GameResult.BlackWin) =>
          val w = map(pairing.whiteId)
          val b = map(pairing.blackId)
          map.updated(pairing.whiteId, w.copy(played = w.played + 1, losses = w.losses + 1, score = w.score + 0.0))
            .updated(pairing.blackId, b.copy(played = b.played + 1, wins = b.wins + 1, score = b.score + 1.0))
        case Some(GameResult.Draw) =>
          val w = map(pairing.whiteId)
          val b = map(pairing.blackId)
          map.updated(pairing.whiteId, w.copy(played = w.played + 1, draws = w.draws + 1, score = w.score + 0.5))
            .updated(pairing.blackId, b.copy(played = b.played + 1, draws = b.draws + 1, score = b.score + 0.5))
    }
    updated.values.toList.sortBy(s => (-s.score, -s.wins, s.name))

  private def maybeAdvanceRound(tournament: Tournament): Tournament =
    tournament.format match
      case TournamentFormat.RoundRobin =>
        // Round-robin is fully scheduled at start; just finish when all rounds complete
        if tournament.roundsData.forall(r => allPairingsFinished(r.pairings)) && tournament.roundsData.size == tournament.rounds then
          tournament.copy(status = TournamentStatus.Finished, finishedAt = Some(Instant.now()))
        else tournament
      case TournamentFormat.Swiss | TournamentFormat.Arena =>
        val currentRound = tournament.roundsData.lastOption
        val roundComplete = currentRound.exists(r => allPairingsFinished(r.pairings))
        val nextRoundNumber = currentRound.map(_.number + 1).getOrElse(1)
        if roundComplete && nextRoundNumber <= tournament.rounds then
          val pairings = generatePairingsForRound(tournament, nextRoundNumber)
          val newRound = Round(nextRoundNumber, pairings, Some(Instant.now()))
          tournament.copy(roundsData = tournament.roundsData :+ newRound)
        else if roundComplete && nextRoundNumber > tournament.rounds then
          tournament.copy(status = TournamentStatus.Finished, finishedAt = Some(Instant.now()))
        else tournament
