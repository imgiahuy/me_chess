# Chess Implementation Features

## 1. Special Moves Implementation

### Castling
- **King-side Castling (0-0)**: King moves from e1 to g1, rook from h1 to f1
- **Queen-side Castling (0-0-0)**: King moves from e1 to c1, rook from a1 to d1
- **Requirements**:
  - King and rook must not have moved before
  - No pieces between king and rook
  - King is not in check
  - King doesn't pass through check
  - King doesn't end in check

**Usage Example**: 
```scala
val move = Move(Position(4, 0), Position(6, 0), Some(CastlingKingSide))
GameService.applyMove(state, move)
```

### En Passant
- Pawn captures opponent's pawn that just moved 2 squares forward
- Only valid on the rank immediately after the opponent's double pawn move
- Must be played immediately on the next move

**Implementation**: En passant captures are detected by checking:
1. Move is a diagonal pawn move to an empty square
2. Last move was a double pawn advance by the opponent
3. Special move type is marked as `EnPassant`

**Usage Example**:
```scala
val move = Move(Position(4, 4), Position(5, 5), Some(EnPassant))
GameService.applyMove(state, move)
```

### Pawn Promotion
- When a pawn reaches the opposite end of the board, it must be promoted
- Can promote to: Queen, Rook, Bishop, or Knight
- Promotion happens automatically when the promotion piece type is specified

**Usage Example**:
```scala
// Promote to Queen
val moveQ = Move(Position(4, 6), Position(4, 7), Some(Promotion(Queen)))
GameService.applyMove(state, moveQ)

// Promote to Knight
val moveN = Move(Position(4, 6), Position(4, 7), Some(Promotion(Knight)))
GameService.applyMove(state, moveN)
```

---

## 2. Draw Conditions

The system now tracks all major draw conditions:

### Stalemate
- Current player is not in check and has no legal moves
- **Detection**: `GameService.isStalemate(state)`

### Insufficient Material
- Only kings remain, or only kings and minor pieces (knights/bishops) remain
- **Conditions**:
  - King vs King
  - King + Knight vs King
  - King + Bishop vs King
  - King + Knight vs King + Knight (same color bishop)

**Detection**: `GameService.hasSufficientMaterial(board)`

### Fifty-Move Rule
- 50 consecutive moves without a pawn move or capture
- Tracked via `halfmovesSinceLastCaptureOrPawn` counter in PositionState
- Game auto-draws after 100 halfmoves

**Tracking**: Automatically incremented on each move, reset on captures/pawn moves

### Threefold Repetition
- Same position occurs three times
- Tracked via `positionHistory` in PositionState
- Automatically detected after each move

**Detection**: `GameService.countBoardRepetitions(snapshot)`

### Mutual Agreement
- Players can agree to a draw at any time

**Usage**:
```scala
val newState = GameService.offerDraw(state)
```

---

## 3. Resignation Feature

Players can resign at any time to concede defeat:

```scala
// White resigns, Black wins
val newState = GameService.resign(state, White)

// Black resigns, White wins
val newState = GameService.resign(state, Black)
```

The game result is immediately set to `Resignation(winner)` where winner is the opponent.

---

## 4. Time Control System

### Time Control Configuration

```scala
// Predefined time controls
val blitz = TimeControl.BLITZ           // 3+2
val rapid = TimeControl.RAPID           // 10+5
val classical = TimeControl.CLASSICAL   // 90+30
val bullet = TimeControl.BULLET         // 1 minute

// Custom time control
val custom = TimeControl(
  initialTimeMs = 10 * 60 * 1000,  // 10 minutes
  incrementMs = 5 * 1000            // 5 second increment
)
```

### Creating a Game with Time Control

```scala
val state = GameService.createGame(
  "White Player",
  "Black Player",
  Some(TimeControl.BLITZ)
)

// Or via controller
val controller = new GameController
val state = controller.createWithTimeControl(
  "White Player",
  "Black Player",
  TimeControl.RAPID
)
```

### Managing Time

```scala
// Get remaining time for a player (in milliseconds)
val whiteTimeRemaining: Option[Long] = GameService.getRemainingTime(state, White)

// Update time after a move
val timeSpentMs = 5000  // 5 seconds
val newState = GameService.updateTimeAfterMove(state, timeSpentMs)

// Check if time is expired
if (GameService.isTimeExpired(state, White)) {
  println("White is out of time - Black wins!")
}
```

### Time Control Features

- **Automatic Increment**: Specified milliseconds are added after each move
- **Time Display**: `PlayerTime.getCurrentTime` gives current time accounting for elapsed time
- **Time Over Detection**: Game automatically ends with `TimeOut(winner)` result
- **Flexible**: Can create games with unlimited time by not specifying TimeControl

---

## 5. Chess Bot System

### Architecture

The bot system is designed for easy maintenance and extension:

- **Bot Trait**: Base interface for all bots
- **BotFactory**: Creates bots by name
- **BotService**: Handles bot interactions
- **Extensible**: Easy to add new bot implementations

```scala
trait Bot {
  def name: String
  def difficulty: String
  def selectMove(state: PositionState, availableMoves: List[Move]): Move
}
```

### Available Bots

#### 1. Random Bot (Easy)
```scala
val bot = BotService.createBot("random")
```
- Selects moves randomly from available legal moves
- Useful for testing and beginners

#### 2. Capture Preference Bot (Medium)
```scala
val bot = BotService.createBot("capture")
```
- Prioritizes capturing pieces
- Prefers moves toward center
- Scoring: 100 points for capture, 10 for center control

### Using Bots

```scala
// Get available bot types
val types: List[String] = BotService.availableBots

// Create a bot
val bot = BotService.createBot("capture")

// Get bot's move
val botMoveResult: Either[String, Move] = BotService.getBotMove(bot, state)

// Use bot to play a move directly
val newStateResult: Either[String, PositionState] = BotService.playBotMove(bot, state)

// Via controller
val controller = new GameController
val newState = controller.playBotMove(bot, state) match {
  case Right(newState) => println("Bot played successfully")
  case Left(error) => println(s"Bot error: $error")
}
```

### Extending the Bot System

To add a new bot implementation:

```scala
// 1. Create a new bot class
class MiniMaxBot extends Bot {
  override def name: String = "MiniMax Bot"
  override def difficulty: String = "Hard"
  
  override def selectMove(state: PositionState, availableMoves: List[Move]): Move = {
    // Your minimax evaluation logic here
    availableMoves.head  // Placeholder
  }
}

// 2. Register it in BotFactory
object BotFactory {
  def createBot(botType: String): Bot = botType.toLowerCase match {
    case "random" => new RandomBot()
    case "capture" => new CapturePreferenceBot()
    case "minimax" => new MiniMaxBot()
    case _ => new RandomBot()
  }
  
  def availableBots: List[String] = List("random", "capture", "minimax")
}
```

---

## PositionState Enhancements

The PositionState now includes additional fields to track all game aspects:

```scala
case class PositionState(
  board: Board,
  turn: Color,
  moveHistory: List[Move],
  whitePlayer: Player,
  blackPlayer: Player,
  creationDate: LocalDate = LocalDate.now(),
  id: Option[String] = None,
  
  // Time control
  whiteTime: Option[PlayerTime] = None,
  blackTime: Option[PlayerTime] = None,
  
  // Draw conditions tracking
  halfmovesSinceLastCaptureOrPawn: Int = 0,
  positionHistory: List[Board] = List.empty,
  
  // Game status
  hasWhiteResigned: Boolean = false,
  hasBlackResigned: Boolean = false,
  gameResult: GameResult = Ongoing
)
```

---

## Game Result Types

```scala
sealed trait GameResult
case object Ongoing extends GameResult
case class Checkmate(winner: Color) extends GameResult
case class Draw(reason: DrawReason) extends GameResult
case class Resignation(winner: Color) extends GameResult
case class TimeOut(winner: Color) extends GameResult
```

---

## Integration Examples

### Full Game with Bot and Time Control

```scala
val controller = new GameController

// Create game with time control
val state = controller.createWithTimeControl(
  "Human",
  "AI",
  TimeControl.BLITZ
)

// Create bot
val bot = controller.createBot("capture")

// Play moves
var currentState = state

// Human plays
val move1 = Move(Position(4, 1), Position(4, 3))
currentState = controller.makeMove(currentState, "e2e4").getOrElse(currentState)

// Bot plays
currentState = controller.playBotMove(bot, currentState).getOrElse(currentState)

// Check for draws/checkmate
if (currentState.gameResult != Ongoing) {
  println(s"Game Over: ${currentState.gameResult}")
}
```

### Resign Example

```scala
// Player resigns
val newState = GameService.resign(state, White)
// Result: GameResult = Resignation(Black)
```

### Time Management Example

```scala
val startTime = System.currentTimeMillis()
// ... player thinks ...
val endTime = System.currentTimeMillis()
val timeSpent = endTime - startTime

val newState = GameService.updateTimeAfterMove(state, timeSpent.toInt)

// Check remaining time
GameService.getRemainingTime(newState, White) match {
  case Some(ms) => println(s"Time remaining: ${ms / 1000} seconds")
  case None => println("No time control")
}
```


