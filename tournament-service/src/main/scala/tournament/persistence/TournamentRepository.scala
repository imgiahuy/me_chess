package tournament.persistence

import tournament.model._

import java.util.UUID
import scala.collection.concurrent.TrieMap

/** Repository trait for tournament persistence. */
trait TournamentRepository:
  def save(tournament: Tournament): Tournament
  def findById(id: String): Option[Tournament]
  def findAll(): List[Tournament]
  def delete(id: String): Boolean

/** In-memory tournament repository. */
class InMemoryTournamentRepository extends TournamentRepository:
  private val store: TrieMap[String, Tournament] = TrieMap.empty

  override def save(tournament: Tournament): Tournament =
    store.put(tournament.id, tournament)
    tournament

  override def findById(id: String): Option[Tournament] =
    store.get(id)

  override def findAll(): List[Tournament] =
    store.values.toList.sortBy(_.createdAt)

  override def delete(id: String): Boolean =
    store.remove(id).isDefined
