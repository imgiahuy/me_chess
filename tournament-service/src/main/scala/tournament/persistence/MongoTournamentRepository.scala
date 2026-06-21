package tournament.persistence

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection}
import com.mongodb.client.model.{Filters, IndexOptions, Sorts, Updates}
import org.bson.Document
import tournament.model._

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters._

/** MongoDB-backed tournament repository. */
class MongoTournamentRepository(client: MongoClient, databaseName: String) extends TournamentRepository:

  private val database = client.getDatabase(databaseName)
  private val collection: MongoCollection[Document] = database.getCollection("tournaments")

  try
    collection.createIndex(new Document("id", 1), new IndexOptions().unique(true))
  catch
    case _: Exception => ()

  override def save(tournament: Tournament): Tournament =
    collection.replaceOne(
      Filters.eq("id", tournament.id),
      toDocument(tournament),
      com.mongodb.client.model.ReplaceOptions().upsert(true)
    )
    tournament

  override def findById(id: String): Option[Tournament] =
    Option(collection.find(Filters.eq("id", id)).first()).map(fromDocument)

  override def findAll(): List[Tournament] =
    collection.find().sort(Sorts.descending("createdAt")).asScala.map(fromDocument).toList

  override def delete(id: String): Boolean =
    collection.deleteOne(Filters.eq("id", id)).getDeletedCount > 0

  private def toDocument(t: Tournament): Document =
    val doc = new Document()
      .append("id", t.id)
      .append("name", t.name)
      .append("format", t.format.toString)
      .append("rounds", t.rounds)
      .append("gamesPerPairing", t.gamesPerPairing)
      .append("timeControlSeconds", t.timeControlSeconds)
      .append("incrementSeconds", t.incrementSeconds)
      .append("status", t.status.toString)
      .append("createdAt", t.createdAt.toString)
      .append("participants", t.participants.map { p =>
        new Document()
          .append("id", p.id)
          .append("name", p.name)
          .append("botType", p.botType.orNull)
          .append("initialRating", p.initialRating)
      }.asJava)
      .append("roundsData", t.roundsData.map { r =>
        new Document()
          .append("number", r.number)
          .append("pairings", r.pairings.map { p =>
            new Document()
              .append("whiteId", p.whiteId)
              .append("blackId", p.blackId)
              .append("round", p.round)
              .append("gameId", p.gameId.orNull)
              .append("result", p.result.map(_.toString).orNull)
          }.asJava)
          .append("startedAt", r.startedAt.map(_.toString).orNull)
          .append("finishedAt", r.finishedAt.map(_.toString).orNull)
      }.asJava)
      .append("standings", t.standings.map { s =>
        new Document()
          .append("participantId", s.participantId)
          .append("name", s.name)
          .append("played", s.played)
          .append("wins", s.wins)
          .append("draws", s.draws)
          .append("losses", s.losses)
          .append("score", s.score)
          .append("rating", s.rating)
      }.asJava)
    t.description.foreach(d => doc.append("description", d))
    t.startedAt.foreach(i => doc.append("startedAt", i.toString))
    t.finishedAt.foreach(i => doc.append("finishedAt", i.toString))
    doc

  private def fromDocument(doc: Document): Tournament =
    val participants = doc.getList("participants", classOf[Document]).asScala.toList.map { d =>
      Participant(
        id = d.getString("id"),
        name = d.getString("name"),
        botType = Option(d.getString("botType")),
        initialRating = d.getDouble("initialRating")
      )
    }
    val roundsData = Option(doc.getList("roundsData", classOf[Document])).map(_.asScala.toList).getOrElse(Nil).map { r =>
      Round(
        number = r.getInteger("number"),
        pairings = r.getList("pairings", classOf[Document]).asScala.toList.map { p =>
          Pairing(
            whiteId = p.getString("whiteId"),
            blackId = p.getString("blackId"),
            round = p.getInteger("round"),
            gameId = Option(p.getString("gameId")),
            result = Option(p.getString("result")).flatMap(GameResult.fromString)
          )
        },
        startedAt = Option(r.getString("startedAt")).map(Instant.parse),
        finishedAt = Option(r.getString("finishedAt")).map(Instant.parse)
      )
    }
    val standings = Option(doc.getList("standings", classOf[Document])).map(_.asScala.toList).getOrElse(Nil).map { s =>
      Standing(
        participantId = s.getString("participantId"),
        name = s.getString("name"),
        played = s.getInteger("played", 0),
        wins = s.getInteger("wins", 0),
        draws = s.getInteger("draws", 0),
        losses = s.getInteger("losses", 0),
        score = s.getDouble("score"),
        rating = s.getDouble("rating")
      )
    }
    Tournament(
      id = doc.getString("id"),
      name = doc.getString("name"),
      format = TournamentFormat.valueOf(doc.getString("format")),
      rounds = doc.getInteger("rounds"),
      gamesPerPairing = doc.getInteger("gamesPerPairing"),
      timeControlSeconds = doc.getInteger("timeControlSeconds"),
      incrementSeconds = doc.getInteger("incrementSeconds"),
      description = Option(doc.getString("description")),
      status = TournamentStatus.valueOf(doc.getString("status")),
      participants = participants,
      roundsData = roundsData,
      standings = standings,
      createdAt = Option(doc.getString("createdAt")).map(Instant.parse).getOrElse(Instant.now()),
      startedAt = Option(doc.getString("startedAt")).map(Instant.parse),
      finishedAt = Option(doc.getString("finishedAt")).map(Instant.parse)
    )

object MongoTournamentRepository:
  def fromEnv(): MongoTournamentRepository =
    val host = sys.env.getOrElse("MONGODB_HOST", "localhost")
    val port = sys.env.getOrElse("MONGODB_PORT", "27017")
    val database = sys.env.getOrElse("MONGODB_DATABASE", "chess")
    val uri = s"mongodb://$host:$port"
    val client = MongoClients.create(uri)
    val db = client.getDatabase(database)
    db.runCommand(new Document("ping", 1))
    val repo = new MongoTournamentRepository(client, database)
    val count = repo.findAll().size
    println(s"[INFO] MongoDB tournament repository ready — $count existing tournaments loaded")
    repo
