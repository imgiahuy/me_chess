package openings

import model._
import scala.collection.mutable

/** Represents a chess opening with its moves and metadata */
case class Opening(
  name: String,
  ecoCode: String,
  moves: List[String],  // UCI move strings
  description: String,
  category: String  // "open", "semi-open", "closed", "indian", "flank", etc.
)

/** Result of opening lookup */
case class OpeningMatch(
  opening: Opening,
  matchedMoves: Int,
  confidence: Double  // 0.0 to 1.0
)

/** Opening catalog for managing and looking up chess openings */
trait OpeningCatalog {
  
  /** Find opening that matches the given move sequence */
  def findOpening(moves: List[String]): Option[OpeningMatch]
  
  /** Get all openings */
  def allOpenings: List[Opening]
  
  /** Get openings by category */
  def openingsByCategory(category: String): List[Opening]
  
  /** Get opening by ECO code */
  def openingByEco(ecoCode: String): Option[Opening]
  
  /** Add a custom opening */
  def addOpening(opening: Opening): Unit
  
  /** Remove an opening by ECO code */
  def removeOpening(ecoCode: String): Unit
  
  /** Search openings by name */
  def searchOpenings(query: String): List[Opening]
}

/** In-memory implementation of opening catalog */
class InMemoryOpeningCatalog extends OpeningCatalog {
  
  private val openings: mutable.Map[String, Opening] = mutable.Map.empty
  private val moveIndex: mutable.Map[String, List[Opening]] = mutable.Map.empty
  
  // Initialize with standard openings
  initializeStandardOpenings()
  
  private def initializeStandardOpenings(): Unit = {
    // Open Games
    addOpening(Opening("Ruy Lopez", "C78", List("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"), "Spanish opening, one of the oldest and most respected openings", "open"))
    addOpening(Opening("Italian Game", "C50", List("e2e4", "e7e5", "g1f3", "b8c6", "f1c4"), "Classical opening with rapid development", "open"))
    addOpening(Opening("Scotch Game", "C45", List("e2e4", "e7e5", "g1f3", "b8c6", "d2d4"), "Solid opening with immediate central tension", "open"))
    addOpening(Opening("Four Knights", "C48", List("e2e4", "e7e5", "g1f3", "b8c6", "b1c3", "g8f6"), "Classical symmetrical development", "open"))
    addOpening(Opening("Three Knights", "C46", List("e2e4", "e7e5", "g1f3", "b8c6", "b1c3"), "Flexible knight development", "open"))
    addOpening(Opening("King's Pawn Opening", "C20", List("e2e4"), "The most popular opening move", "open"))
    addOpening(Opening("Center Game", "C22", List("e2e4", "e7e5", "d2d4", "e5d4", "q1d4", "b8c6", "d4d1"), "Aggressive central control", "open"))
    addOpening(Opening("Danish Gambit", "C21", List("e2e4", "e7e5", "d2d4", "e5d4", "c2c3", "d4c3"), "Sacrificial opening for rapid development", "open"))
    addOpening(Opening("Bishop's Opening", "C23", List("e2e4", "e7e5", "f1c4"), "Develops bishop early to control diagonal", "open"))
    addOpening(Opening("Portuguese Opening", "C20", List("e2e4", "e7e5", "d2d4", "b8c6", "b1c3"), "Unusual but solid", "open"))
    
    // Semi-Open Games
    addOpening(Opening("Sicilian Defense", "B20", List("e2e4", "c7c5"), "Most popular response to 1.e4", "semi-open"))
    addOpening(Opening("Open Sicilian", "B20", List("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4"), "Main line with open center", "semi-open"))
    addOpening(Opening("Najdorf Sicilian", "B90", List("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6"), "Sharp and complex, Kasparov's favorite", "semi-open"))
    addOpening(Opening("Dragon Sicilian", "B70", List("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "g7g6", "f1e2", "f8g7"), "Aggressive with kingside fianchetto", "semi-open"))
    addOpening(Opening("French Defense", "C00", List("e2e4", "e7e6"), "Solid and counter-attacking", "semi-open"))
    addOpening(Opening("Caro-Kann Defense", "B10", List("e2e4", "c7c6"), "Very solid defense", "semi-open"))
    addOpening(Opening("Pirc Defense", "B07", List("e2e4", "d7d6", "d2d4", "g8f6", "b1c3", "g7g6"), "Hypermodern with kingside fianchetto", "semi-open"))
    addOpening(Opening("Modern Defense", "A40", List("e2e4", "g7g6"), "Flexible hypermodern defense", "semi-open"))
    addOpening(Opening("Alekhine Defense", "B02", List("e2e4", "g8f6"), "Provocative defense inviting central pawn push", "semi-open"))
    addOpening(Opening("Scandinavian Defense", "B01", List("e2e4", "d7d5"), "Immediate counter-attack in center", "semi-open"))
    
    // Closed Games
    addOpening(Opening("Queen's Gambit", "D06", List("d2d4", "d7d5", "c2c4"), "Classical queen's pawn opening", "closed"))
    addOpening(Opening("Queen's Gambit Accepted", "D20", List("d2d4", "d7d5", "c2c4", "d5c4"), "Accepts the gambit pawn", "closed"))
    addOpening(Opening("Queen's Gambit Declined", "D30", List("d2d4", "d7d5", "c2c4", "e7e6"), "Solid refusal of the gambit", "closed"))
    addOpening(Opening("Slav Defense", "D10", List("d2d4", "d7d5", "c2c4", "c7c6"), "Very solid defense to Queen's Gambit", "closed"))
    addOpening(Opening("King's Indian Defense", "E60", List("d2d4", "g8f6", "c2c4", "g7g6", "g1f3", "d7d6"), "Aggressive hypermodern defense", "indian"))
    addOpening(Opening("Nimzo-Indian Defense", "E20", List("d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "b8c4"), "Controls center with bishop pin", "indian"))
    addOpening(Opening("Queen's Indian Defense", "E12", List("d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "b7b6"), "Solid hypermodern defense", "indian"))
    addOpening(Opening("Catalan Opening", "E01", List("d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "d7d5", "g2g3"), "Combines Queen's Gambit with kingside fianchetto", "indian"))
    addOpening(Opening("Grünfeld Defense", "D80", List("d2d4", "g8f6", "c2c4", "d7d5", "g1f3", "g7g6", "c4d5", "f6d5"), "Counter-attacking hypermodern defense", "indian"))
    addOpening(Opening("Benoni Defense", "A60", List("d2d4", "g8f6", "c2c4", "c7c5"), "Aggressive counter-attacking defense", "indian"))
    addOpening(Opening("Dutch Defense", "A80", List("d2d4", "f7f5"), "Aggressive kingside fianchetto", "flank"))
    addOpening(Opening("English Opening", "A10", List("c2c4"), "Flexible opening, can transpose to many systems", "flank"))
    addOpening(Opening("Reti Opening", "A04", List("g1f3", "d7d5", "c2c4"), "Hypermodern with delayed central control", "flank"))
    addOpening(Opening("King's Fianchetto Opening", "A00", List("g2g3", "e7e5", "f1g2"), "Develops bishop with fianchetto", "flank"))
    addOpening(Opening("Bird's Opening", "A03", List("f2f4"), "Flank opening controlling e5", "flank"))
    addOpening(Opening("Larsen's Opening", "A01", List("b1c3"), "Flexible knight development", "flank"))
    addOpening(Opening("Sokolsky Opening", "A00", List("b2b4"), "Polish opening with queenside expansion", "flank"))
  }
  
  override def findOpening(moves: List[String]): Option[OpeningMatch] = {
    if (moves.isEmpty) return None
    
    val moveKey = moves.mkString(" ")
    var bestMatch: Option[OpeningMatch] = None
    var bestScore = 0
    
    openings.values.foreach { opening =>
      val matchedMoves = countMatchingMoves(moves, opening.moves)
      if (matchedMoves > bestScore && matchedMoves > 0) {
        bestScore = matchedMoves
        val confidence = matchedMoves.toDouble / opening.moves.length
        bestMatch = Some(OpeningMatch(opening, matchedMoves, confidence))
      }
    }
    
    bestMatch
  }
  
  private def countMatchingMoves(gameMoves: List[String], openingMoves: List[String]): Int = {
    gameMoves.zip(openingMoves).takeWhile { case (gm, om) => gm == om }.length
  }
  
  override def allOpenings: List[Opening] = openings.values.toList
  
  override def openingsByCategory(category: String): List[Opening] = {
    openings.values.filter(_.category == category).toList
  }
  
  override def openingByEco(ecoCode: String): Option[Opening] = {
    openings.values.find(_.ecoCode == ecoCode)
  }
  
  override def addOpening(opening: Opening): Unit = {
    openings(opening.ecoCode) = opening
    
    // Index by move sequences
    for (i <- 1 to opening.moves.length) {
      val moveSeq = opening.moves.take(i).mkString(" ")
      val existing = moveIndex.getOrElse(moveSeq, List.empty)
      moveIndex(moveSeq) = opening :: existing
    }
  }
  
  override def removeOpening(ecoCode: String): Unit = {
    openings.get(ecoCode).foreach { opening =>
      // Remove from move index
      for (i <- 1 to opening.moves.length) {
        val moveSeq = opening.moves.take(i).mkString(" ")
        moveIndex.get(moveSeq).foreach { list =>
          val updated = list.filter(_.ecoCode != ecoCode)
          if (updated.isEmpty) moveIndex.remove(moveSeq)
          else moveIndex(moveSeq) = updated
        }
      }
      openings.remove(ecoCode)
    }
  }
  
  override def searchOpenings(query: String): List[Opening] = {
    val lowerQuery = query.toLowerCase
    openings.values.filter { opening =>
      opening.name.toLowerCase.contains(lowerQuery) ||
      opening.ecoCode.toLowerCase.contains(lowerQuery) ||
      opening.description.toLowerCase.contains(lowerQuery)
    }.toList
  }
}

/** Singleton opening catalog instance */
object OpeningCatalog {
  private val instance = new InMemoryOpeningCatalog()
  
  def apply(): OpeningCatalog = instance
  
  /** Get opening by ECO code (convenience method) */
  def byEco(ecoCode: String): Option[Opening] = instance.openingByEco(ecoCode)
  
  /** Find opening by move sequence (convenience method) */
  def find(moves: List[String]): Option[OpeningMatch] = instance.findOpening(moves)
  
  /** Search openings (convenience method) */
  def search(query: String): List[Opening] = instance.searchOpenings(query)
  
  /** Get all categories */
  def categories: List[String] = instance.allOpenings.map(_.category).distinct
}
