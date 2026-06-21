package tournament.engine

import tournament.model.{Participant, Pairing, Round}

/** Generates pairings for tournament rounds. */
object PairingEngine:

  /** Round-robin pairing using the circle method.
   * Each participant plays every other participant twice (once as white, once as black).
   */
  def roundRobin(participants: List[Participant], gamesPerPairing: Int): List[Round] =
    if participants.size < 2 then Nil
    else
      val n = participants.size
      val roundsNeeded = (n - 1) * gamesPerPairing
      val ids = participants.map(_.id)
      val padded = if n % 2 == 0 then ids else ids :+ "__bye__"
      val m = padded.size
      val fixed = padded.head
      val rotating = padded.tail.toArray

      (0 until roundsNeeded).map { r =>
        val roundIndex = r / gamesPerPairing
        val colorSwap = (r % gamesPerPairing) == 1
        val positions = Array(fixed) ++ rotating
        val half = m / 2
        val pairings = (0 until half).map { i =>
          val whiteIdx = i
          val blackIdx = m - 1 - i
          val white = positions(whiteIdx)
          val black = positions(blackIdx)
          if white == "__bye__" || black == "__bye__" then None
          else if colorSwap then Some(Pairing(black, white, r + 1))
          else Some(Pairing(white, black, r + 1))
        }.flatten.toList
        // Rotate the circle for the next round
        if (r + 1) % gamesPerPairing == 0 then
          val last = rotating(rotating.length - 1)
          System.arraycopy(rotating, 0, rotating, 1, rotating.length - 1)
          rotating(0) = last
        Round(number = r + 1, pairings = pairings)
      }.toList

  /** Swiss pairing using a simple greedy top-half vs bottom-half strategy.
   * Avoids rematches when possible. For a full implementation, use a weighted matching algorithm.
   */
  def swiss(participants: List[Participant], roundNumber: Int, previousPairings: Set[(String, String)]): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val sorted = participants.map(_.id).sorted
      val half = sorted.size / 2
      val top = sorted.take(half)
      val bottom = sorted.drop(half).reverse
      top.zip(bottom).map { case (w, b) =>
        // Avoid duplicate orientation if these two already played with the same colors
        val alreadyPlayed = previousPairings.contains((w, b)) || previousPairings.contains((b, w))
        if alreadyPlayed && roundNumber % 2 == 0 then Pairing(b, w, roundNumber)
        else Pairing(w, b, roundNumber)
      }.toList

  /** Arena pairing: pair active participants by current score (closest scores first). */
  def arena(
    participants: List[Participant],
    roundNumber: Int,
    standings: Map[String, Double]
  ): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val sorted = participants.map(_.id).sortBy(id => -standings.getOrElse(id, 0.0))
      val pairs = sorted.grouped(2).collect { case List(a, b) => Pairing(a, b, roundNumber) }.toList
      // Swap colors for every other round to balance
      if roundNumber % 2 == 0 then pairs.map(p => Pairing(p.blackId, p.whiteId, roundNumber))
      else pairs

  /** Single elimination pairing: bracket-style knockout tournament. */
  def singleElimination(participants: List[Participant], roundNumber: Int): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val ids = participants.map(_.id)
      // Power of 2 bracket: add byes if needed
      val bracketSize = nextPowerOfTwo(ids.size)
      val padded = ids ++ List.fill(bracketSize - ids.size)("__bye__")
      // Calculate which round this is based on bracket size
      val totalRounds = log2(bracketSize)
      val matchesInThisRound = bracketSize / (1 << roundNumber)
      val startIndex = if roundNumber == 1 then 0 else bracketSize - matchesInThisRound
      val endIndex = startIndex + matchesInThisRound
      val roundParticipants = padded.slice(startIndex, endIndex)
      roundParticipants.grouped(2).collect { 
        case List(a, b) if a != "__bye__" && b != "__bye__" => 
          Pairing(a, b, roundNumber)
        case List(a, "__bye__") => 
          Pairing(a, "__bye__", roundNumber) // Bye - automatic win
      }.toList

  /** Double elimination pairing: winners bracket and losers bracket. */
  def doubleElimination(
    participants: List[Participant],
    roundNumber: Int,
    winnersBracket: List[String],
    losersBracket: List[String]
  ): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val wbPairings = if winnersBracket.size >= 2 then
        winnersBracket.grouped(2).collect { case List(a, b) => Pairing(a, b, roundNumber, Some("wb")) }.toList
      else Nil
      
      val lbPairings = if losersBracket.size >= 2 then
        losersBracket.grouped(2).collect { case List(a, b) => Pairing(a, b, roundNumber, Some("lb")) }.toList
      else Nil
      
      wbPairings ++ lbPairings

  /** Group stage pairing: participants divided into groups, round-robin within each group. */
  def groupStage(
    participants: List[Participant],
    roundNumber: Int,
    groupSize: Int = 4
  ): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val groups = participants.map(_.id).grouped(groupSize).toList
      groups.flatMap { group =>
        val n = group.size
        if n < 2 then Nil
        else
          val roundIndex = (roundNumber - 1) % (n - 1)
          val fixed = group.head
          val rotating = group.tail.toArray
          // Rotate for the specific round
          (0 until roundIndex).foreach { _ =>
            val last = rotating(rotating.length - 1)
            System.arraycopy(rotating, 0, rotating, 1, rotating.length - 1)
            rotating(0) = last
          }
          val positions = Array(fixed) ++ rotating
          val half = n / 2
          (0 until half).map { i =>
            val white = positions(i)
            val black = positions(n - 1 - i)
            Pairing(white, black, roundNumber)
          }.toList
      }

  /** League pairing: everyone plays everyone (round-robin) over the entire season. */
  def league(participants: List[Participant], roundNumber: Int, gamesPerPairing: Int = 2): List[Pairing] =
    roundRobin(participants, gamesPerPairing).drop(roundNumber - 1).take(1).headOption.map(_.pairings).getOrElse(Nil)

  /** Random knockout pairing: random pairings each round, no bracket. */
  def randomKnockout(participants: List[Participant], roundNumber: Int): List[Pairing] =
    if participants.size < 2 then Nil
    else
      val ids = scala.util.Random.shuffle(participants.map(_.id))
      ids.grouped(2).collect { 
        case List(a, b) => Pairing(a, b, roundNumber)
        case List(a) => Pairing(a, "__bye__", roundNumber) // Bye for odd number
      }.toList

  // Helper functions
  private def nextPowerOfTwo(n: Int): Int =
    if n <= 1 then 1 else 1 << (32 - Integer.numberOfLeadingZeros(n - 1))

  private def log2(n: Int): Int =
    if n <= 1 then 0 else Integer.numberOfTrailingZeros(n)
