package player

import java.time.Instant
import java.util.UUID
import scala.collection.concurrent.TrieMap

/** In-memory player repository. Can be swapped for a database-backed impl. */
class PlayerRepository {
  private val store: TrieMap[String, Player] = TrieMap.empty

  def create(req: CreatePlayerRequest): Player = {
    val id = UUID.randomUUID().toString
    val now = Instant.now().toString
    val player = Player(
      id = id,
      username = req.username,
      email = req.email,
      rating = req.initialRating,
      gamesPlayed = 0,
      wins = 0,
      losses = 0,
      draws = 0,
      createdAt = now,
      lastSeenAt = now
    )
    store.put(id, player)
    player
  }

  def findById(id: String): Option[Player] = store.get(id)

  def findByUsername(username: String): Option[Player] =
    store.values.find(_.username == username)

  def findAll(): List[Player] = store.values.toList.sortBy(_.username)

  def update(id: String, req: UpdatePlayerRequest): Option[Player] =
    store.get(id).map { p =>
      val updated = p.copy(
        email = req.email.orElse(p.email),
        rating = req.rating.getOrElse(p.rating),
        lastSeenAt = Instant.now().toString
      )
      store.put(id, updated)
      updated
    }

  def recordGame(req: RecordGameRequest): Option[Player] =
    store.get(req.playerId).map { p =>
      req.result.toLowerCase match {
        case "win" =>
          val updated = p.copy(
            rating = math.max(0, p.rating + req.ratingChange),
            gamesPlayed = p.gamesPlayed + 1,
            wins = p.wins + 1,
            lastSeenAt = Instant.now().toString
          )
          store.put(req.playerId, updated)
          updated
        case "loss" =>
          val updated = p.copy(
            rating = math.max(0, p.rating + req.ratingChange),
            gamesPlayed = p.gamesPlayed + 1,
            losses = p.losses + 1,
            lastSeenAt = Instant.now().toString
          )
          store.put(req.playerId, updated)
          updated
        case "draw" =>
          val updated = p.copy(
            rating = math.max(0, p.rating + req.ratingChange),
            gamesPlayed = p.gamesPlayed + 1,
            draws = p.draws + 1,
            lastSeenAt = Instant.now().toString
          )
          store.put(req.playerId, updated)
          updated
        case _ =>
          // Invalid result: no changes
          p
      }
    }

  def delete(id: String): Boolean = store.remove(id).isDefined

  def leaderboard(limit: Int = 20): List[PlayerStats] =
    store.values.toList
      .sortBy(-_.rating)
      .take(limit)
      .zipWithIndex
      .map { case (p, idx) =>
        PlayerStats(
          playerId = p.id,
          username = p.username,
          rating = p.rating,
          gamesPlayed = p.gamesPlayed,
          wins = p.wins,
          losses = p.losses,
          draws = p.draws,
          winRate = p.winRate,
          rank = idx + 1
        )
      }
}
