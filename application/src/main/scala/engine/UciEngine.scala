package engine

import model._
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import scala.util.{Try, Success, Failure}
import org.slf4j.LoggerFactory

/** UCI (Universal Chess Interface) engine client for communicating with external chess engines like Stockfish */
class UciEngine(enginePath: String, options: Map[String, String] = Map.empty) extends Bot {

  private val logger = LoggerFactory.getLogger(getClass)
  private var process: Option[Process] = None
  private var reader: Option[BufferedReader] = None
  private var writer: Option[BufferedWriter] = None
  private var isReady = false
  private var positionFen: String = ""
  
  // Queue for async responses
  private val responseQueue = new ArrayBlockingQueue[String](100)
  private var bestMovePromise: Option[Promise[String]] = None
  private var searchOutput: java.util.ArrayList[String] = new java.util.ArrayList[String]()
  
  override def name: String = s"UCI Engine (${enginePath.split(java.io.File.separator).last})"
  override def difficulty: String = "Expert"
  override def description: String = "External UCI-compliant chess engine (e.g., Stockfish). Provides strong play and analysis."
  
  /** Initialize the engine process */
  def initialize(): Try[Unit] = Try {
    logger.info(s"Initializing UCI engine at: $enginePath with options: $options")
    val pb = new ProcessBuilder(enginePath)
    pb.redirectErrorStream(true)
    process = Some(pb.start())
    
    reader = Some(new BufferedReader(new InputStreamReader(process.get.getInputStream)))
    writer = Some(new BufferedWriter(new OutputStreamWriter(process.get.getOutputStream)))
    
    // Start response reader thread
    new Thread(() => readResponses()).start()
    
    // Send UCI command
    sendCommand("uci")
    waitForResponse("uciok", 5000)
    
    // Set engine options
    options.foreach { case (name, value) =>
      sendCommand(s"setoption name $name value $value")
    }
    
    // Send isready to check if engine is ready
    sendCommand("isready")
    waitForResponse("readyok", 5000)
    
    isReady = true
  }
  
  /** Read responses from engine in background thread */
  private def readResponses(): Unit = {
    try {
      while (process.isDefined && reader.isDefined) {
        val line = reader.get.readLine()
        if (line == null) {
          // Engine process ended
          logger.error("Engine process ended (stream closed)")
          shutdown()
        } else {
          handleResponse(line)
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error reading responses from engine: ${e.getMessage}", e)
        shutdown()
    }
  }
  
  /** Handle engine responses */
  private def handleResponse(line: String): Unit = {
    responseQueue.offer(line)
    
    // Accumulate info lines during search
    if (line.startsWith("info")) {
      searchOutput.synchronized {
        searchOutput.add(line)
      }
    }
    
    if (line.startsWith("bestmove")) {
      // Include all accumulated info lines in the response
      val fullOutput = searchOutput.synchronized {
        val output = searchOutput.toArray.mkString("\n")
        searchOutput.clear()
        output
      }
      bestMovePromise.foreach(_.success(fullOutput + "\n" + line))
      bestMovePromise = None
    }
  }
  
  /** Send command to engine */
  private def sendCommand(command: String): Unit = {
    writer.foreach { w =>
      w.write(command)
      w.newLine()
      w.flush()
    }
  }
  
  /** Wait for specific response with timeout */
  private def waitForResponse(expected: String, timeoutMs: Long): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val response = responseQueue.poll(100, TimeUnit.MILLISECONDS)
      if (response != null && response.contains(expected)) {
        return true
      }
    }
    false
  }
  
  /** Set position using FEN string */
  def setPosition(fen: String): Unit = {
    positionFen = fen
    sendCommand(s"position fen $fen")
  }
  
  /** Set position using moves from start position */
  def setPosition(moves: List[String]): Unit = {
    val movesStr = moves.mkString(" ")
    sendCommand(s"position startpos moves $movesStr")
  }
  
  /** Go: start calculating on current position */
  def go(depth: Int = 15, timeMs: Long = 1000): Future[String] = {
    logger.info(s"Starting search with depth=$depth, timeMs=$timeMs")
    if (!isReady) {
      logger.info("Engine not ready, initializing...")
      initialize() match {
        case Failure(e) => return Future.failed(e)
        case Success(_) =>
      }
    }

    val promise = Promise[String]()
    bestMovePromise = Some(promise)

    val command = s"go depth $depth movetime $timeMs"
    logger.info(s"Sending UCI command: $command")
    try {
      sendCommand(command)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to send command to engine: ${e.getMessage}, attempting restart", e)
        shutdown()
        Thread.sleep(500)
        initialize() match {
          case Failure(e2) => return Future.failed(e2)
          case Success(_) =>
            logger.info("Engine restarted successfully, retrying command")
            sendCommand(command)
        }
    }
    
    // Timeout fallback
    Future {
      Thread.sleep(timeMs + 2000)
      if (!promise.isCompleted) {
        promise.tryFailure(new Exception("Engine timeout"))
      }
    }
    
    promise.future
  }
  
  /** Stop current calculation */
  def stop(): Unit = {
    sendCommand("stop")
  }
  
  /** Shutdown engine process */
  def shutdown(): Unit = {
    stop()
    sendCommand("quit")
    Thread.sleep(500)
    
    writer.foreach(_.close())
    reader.foreach(_.close())
    process.foreach(_.destroy())
    
    writer = None
    reader = None
    process = None
    isReady = false
  }
  
  /** Get engine info (id, options, etc.) */
  def getEngineInfo: Map[String, String] = {
    sendCommand("uci")
    var info = Map.empty[String, String]
    
    val deadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < deadline) {
      val response = responseQueue.poll(100, TimeUnit.MILLISECONDS)
      if (response != null) {
        if (response.startsWith("id name")) {
          info += ("name" -> response.substring(8).trim)
        } else if (response.startsWith("id author")) {
          info += ("author" -> response.substring(10).trim)
        } else if (response == "uciok") {
          return info
        }
      }
    }
    info
  }
  
  /** Select move using UCI engine */
  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    if (availableMoves.isEmpty) {
      throw new IllegalArgumentException("No legal moves available")
    }
    
    // Convert position to FEN
    val fen = toFen(state)
    setPosition(fen)
    
    // Get best move from engine
    val futureMove = go(depth = 12, timeMs = 500)
    
    try {
      val result = scala.concurrent.Await.result(futureMove, scala.concurrent.duration.Duration(2, "seconds"))
      val bestMoveStr = result.split(" ").drop(1).headOption.getOrElse("")
      
      // Convert UCI move to internal Move
      uciToMove(bestMoveStr, availableMoves).getOrElse(availableMoves.head)
    } catch {
      case _: Exception => availableMoves.head // Fallback to first move on error
    }
  }
  
  /** Convert PositionState to FEN string */
  private def toFen(state: PositionState): String = {
    val board = state.board
    val fen = new StringBuilder()
    
    for (row <- 0 to 7) {
      var emptyCount = 0
      for (col <- 0 to 7) {
        board.pieceAt(Position(col, row)) match {
          case Some(piece) =>
            if (emptyCount > 0) {
              fen.append(emptyCount)
              emptyCount = 0
            }
            fen.append(pieceToFen(piece))
          case None =>
            emptyCount += 1
        }
      }
      if (emptyCount > 0) {
        fen.append(emptyCount)
      }
      if (row < 7) fen.append("/")
    }
    
    fen.append(s" ${state.turn.toString.toLowerCase}")
    fen.append(" w") // Simplified - always assume castling rights
    fen.append(" -") // Simplified - no en passant
    fen.append(" 0 1") // Simplified - move counter
    
    fen.toString
  }
  
  /** Convert piece to FEN character */
  private def pieceToFen(piece: Piece): Char = {
    val base = piece.pieceType match {
      case Pawn => 'p'
      case Knight => 'n'
      case Bishop => 'b'
      case Rook => 'r'
      case Queen => 'q'
      case King => 'k'
    }
    if (piece.color == White) base.toUpper else base
  }
  
  /** Convert UCI move string to internal Move */
  private def uciToMove(uci: String, availableMoves: List[Move]): Option[Move] = {
    if (uci.length < 4) return None
    
    val fromCol = uci.charAt(0) - 'a'
    val fromRow = 8 - (uci.charAt(1) - '0')
    val toCol = uci.charAt(2) - 'a'
    val toRow = 8 - (uci.charAt(3) - '0')
    
    val from = Position(fromCol, fromRow)
    val to = Position(toCol, toRow)
    
    // Handle promotion
    val promotion = if (uci.length == 5) {
      uci.charAt(4).toLower match {
        case 'q' => Some(Queen)
        case 'r' => Some(Rook)
        case 'b' => Some(Bishop)
        case 'n' => Some(Knight)
        case _ => None
      }
    } else None
    
    availableMoves.find { move =>
      move.from == from && move.to == to && 
      (promotion.isEmpty || move.specialMove == promotion.map(model.Promotion.apply))
    }
  }
}

/** Configuration for UCI engine */
case class UciEngineConfig(
  enginePath: String,
  options: Map[String, String] = Map.empty,
  defaultDepth: Int = 15,
  defaultTimeMs: Long = 1000
)

/** Factory for creating UCI engines */
object UciEngine {
  
  /** Create a UCI engine with default configuration */
  def apply(enginePath: String): UciEngine = {
    new UciEngine(enginePath)
  }
  
  /** Create a UCI engine with custom configuration */
  def create(config: UciEngineConfig): UciEngine = {
    new UciEngine(config.enginePath, config.options)
  }
  
  /** Check if Stockfish is available at default path */
  def isStockfishAvailable(path: String = "stockfish"): Boolean = {
    Try {
      val engine = new UciEngine(path)
      engine.initialize()
      val info = engine.getEngineInfo
      engine.shutdown()
      info.contains("name") && info("name").toLowerCase.contains("stockfish")
    }.getOrElse(false)
  }
}
