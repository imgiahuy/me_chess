package chess.benchmark

import org.openjdk.jmh.annotations._
import model.{PositionState, White}
import service.GameService
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
class MoveGenerationBenchmark {

  private var initialState: PositionState = _
  private var midGameState: PositionState = _

  @Setup
  def setup(): Unit = {
    initialState = GameService.createGame("White", "Black", None)
    midGameState = (0 until 10).foldLeft(initialState) { (state, _) =>
      val moves = GameService.getLegalMoves(state, state.turn)
      if (moves.isEmpty) state
      else GameService.applyMove(state, moves.head) match {
        case Right(newState) => newState
        case Left(_) => state
      }
    }
  }

  @Benchmark
  def generateInitialLegalMoves(): List[model.Move] = {
    GameService.getLegalMoves(initialState, White)
  }

  @Benchmark
  def generateMidGameLegalMoves(): List[model.Move] = {
    GameService.getLegalMoves(midGameState, midGameState.turn)
  }

  @Benchmark
  def applyMoveBenchmark(): Either[String, PositionState] = {
    val moves = GameService.getLegalMoves(initialState, White)
    if (moves.isEmpty) Left("No moves")
    else GameService.applyMove(initialState, moves.head)
  }
}
