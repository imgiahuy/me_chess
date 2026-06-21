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
