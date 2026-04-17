# ME Chess REST API Documentation

## Overview

The ME Chess REST API provides a complete interface for managing chess games and playing moves via HTTP. The API follows RESTful principles and uses JSON for request and response bodies.

## Base URL

```
http://localhost:8080/v1/chess
```

## Authentication

Currently, no authentication is required. In production, consider implementing API keys or OAuth 2.0.

## API Endpoints

### 1. Create a New Game

**Endpoint:**
```
POST /v1/chess/games
```

**Description:** Creates a new game session with the standard starting position.

**Request:**
```bash
curl -X POST http://localhost:8080/v1/chess/games
```

**Response (201 Created):**
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Game created successfully"
}
```

---

### 2. Get Game State

**Endpoint:**
```
GET /v1/chess/games/{gameId}
```

**Description:** Retrieves the current state of a specific game.

**Path Parameters:**
- `gameId` (string): The unique identifier of the game

**Request:**
```bash
curl http://localhost:8080/v1/chess/games/550e8400-e29b-41d4-a716-446655440000
```

**Response (200 OK):**
```json
{
  "board": {
    "squares": {
      "a1": { "color": "white", "pieceType": "rook" },
      "b1": { "color": "white", "pieceType": "knight" },
      "e1": { "color": "white", "pieceType": "king" },
      "a8": { "color": "black", "pieceType": "rook" },
      "e8": { "color": "black", "pieceType": "king" }
      // ... all piece positions
    }
  },
  "currentTurn": "white",
  "moveHistory": [],
  "isGameOver": false,
  "winner": null
}
```

---

### 3. Make a Move

**Endpoint:**
```
POST /v1/chess/games/{gameId}/moves
```

**Description:** Applies a move to the game and advances the turn.

**Path Parameters:**
- `gameId` (string): The unique identifier of the game

**Request Body:**
```json
{
  "from": "e2",
  "to": "e4"
}
```

**Full Request Example:**
```bash
curl -X POST http://localhost:8080/v1/chess/games/550e8400-e29b-41d4-a716-446655440000/moves \
  -H "Content-Type: application/json" \
  -d '{"from":"e2","to":"e4"}'
```

**Response (200 OK):**
```json
{
  "board": {
    "squares": {
      "a1": { "color": "white", "pieceType": "rook" },
      "e4": { "color": "white", "pieceType": "pawn" },
      // ... updated positions
    }
  },
  "currentTurn": "black",
  "moveHistory": [
    { "from": "e2", "to": "e4" }
  ],
  "isGameOver": false,
  "winner": null
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "No piece at e2"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found: invalid-game-id"
}
```

---

### 4. List All Active Games

**Endpoint:**
```
GET /v1/chess/games
```

**Description:** Returns a list of all currently active game sessions.

**Request:**
```bash
curl http://localhost:8080/v1/chess/games
```

**Response (200 OK):**
```json
{
  "games": [
    "550e8400-e29b-41d4-a716-446655440000",
    "660e8400-e29b-41d4-a716-446655440001"
  ],
  "count": 2
}
```

---

### 5. Delete a Game

**Endpoint:**
```
DELETE /v1/chess/games/{gameId}
```

**Description:** Deletes/terminates a game session.

**Path Parameters:**
- `gameId` (string): The unique identifier of the game

**Request:**
```bash
curl -X DELETE http://localhost:8080/v1/chess/games/550e8400-e29b-41d4-a716-446655440000
```

**Response (200 OK):**
```json
{
  "message": "Game 550e8400-e29b-41d4-a716-446655440000 deleted successfully"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found: invalid-game-id"
}
```

---

### 6. API Information

**Endpoint:**
```
GET /v1/chess/info
```

**Description:** Returns information about the API and its status.

**Request:**
```bash
curl http://localhost:8080/v1/chess/info
```

**Response (200 OK):**
```json
{
  "name": "ME Chess REST API",
  "version": "1.0.0",
  "status": "running"
}
```

---

## Data Models

### Position Format

Positions are represented in algebraic notation:
- Files: `a` through `h` (columns 0-7)
- Ranks: `1` through `8` (rows 0-7)
- Example: `"e2"`, `"a1"`, `"h8"`

### Move Format

A move consists of a source and destination position:
```json
{
  "from": "e2",
  "to": "e4"
}
```

### Color

- `"white"` - White pieces
- `"black"` - Black pieces

### PieceType

- `"king"`
- `"queen"`
- `"rook"`
- `"bishop"`
- `"knight"`
- `"pawn"`

### Board State

```json
{
  "board": {
    "squares": {
      "a1": { "color": "white", "pieceType": "rook" },
      "a2": { "color": "white", "pieceType": "pawn" },
      // ... all pieces on the board
    }
  },
  "currentTurn": "white",
  "moveHistory": [
    { "from": "e2", "to": "e4" },
    { "from": "e7", "to": "e5" }
  ],
  "isGameOver": false,
  "winner": null
}
```

---

## Error Handling

All error responses follow this format:

```json
{
  "error": "Description of what went wrong"
}
```

### Common HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | OK | Move applied successfully |
| 201 | Created | Game created successfully |
| 400 | Bad Request | Invalid move, out of bounds, etc. |
| 404 | Not Found | Game not found |
| 500 | Server Error | Internal server error |

---

## Usage Examples

### Example 1: Start a new game and make moves

```bash
# 1. Create a game
GAME_ID=$(curl -s -X POST http://localhost:8080/v1/chess/games | jq -r '.gameId')
echo "Created game: $GAME_ID"

# 2. White moves pawn e2-e4
curl -s -X POST http://localhost:8080/v1/chess/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"from":"e2","to":"e4"}' | jq .

# 3. Black moves pawn e7-e5
curl -s -X POST http://localhost:8080/v1/chess/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"from":"e7","to":"e5"}' | jq .

# 4. Check game state
curl http://localhost:8080/v1/chess/games/$GAME_ID | jq .
```

### Example 2: Validate move

```bash
# Try an invalid move (no piece at e3)
curl -X POST http://localhost:8080/v1/chess/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"from":"e3","to":"e4"}' | jq .

# Response:
# {
#   "error": "No piece at e3"
# }
```

---

## Starting the Server

### Method 1: Run the main application

```bash
sbt run
# Choose ChessRestServer when prompted
```

### Method 2: Run directly in SBT

```bash
sbt "runMain presentation.rest.ChessRestServer"
```

### Method 3: Compile and run JAR

```bash
sbt package
java -cp "target/scala-3.3.7/me_chess_3-0.1.0-SNAPSHOT.jar:..." presentation.rest.ChessRestServer
```

---

## Future Enhancements

- [ ] Persistent game storage (database)
- [ ] User authentication and authorization
- [ ] WebSocket support for real-time game updates
- [ ] Move validation and legal move generation
- [ ] Checkmate, stalemate, and draw detection
- [ ] Chess clock / time control
- [ ] Move undo/redo
- [ ] PGN export/import
- [ ] Rating system
- [ ] AI opponent

---

## Development

### Project Structure

```
src/main/scala/presentation/rest/
  ├── ChessRestServer.scala      # Server entry point
  ├── ChessApiRoutes.scala       # Route definitions
  ├── GameRepository.scala       # Game session storage
  └── JsonSerializers.scala      # JSON (de)serialization
```

### Dependencies

- **Akka HTTP**: Web framework
- **Play JSON**: JSON serialization
- **Scala 3.3.7**: Language
- **SBT**: Build tool

## Testing with Postman

### Step 0: Start Server
```powershell
cd C:\sem7\SA\me_chess
sbt run
```

Expected output:
```
[info] Server online at http://localhost:8080/
[info] API Documentation: http://localhost:8080/v1/chess/info
[info] Press ENTER to stop the server...
```

### 1. POST Create Game
- **URL**: `http://localhost:8080/v1/chess/games`
- **Method**: POST
- **Headers**: None required
- **Body**: None
- **Expected**: 200 OK with gameId

### 2. GET Game State
- **URL**: `http://localhost:8080/v1/chess/games/{gameId}`
- **Method**: GET
- **Expected**: 200 OK with board state

### 3. POST Play Move
- **URL**: `http://localhost:8080/v1/chess/games/{gameId}/moves`
- **Method**: POST
- **Headers**: `Content-Type: application/json`
- **Body**:
```json
{
  "from": "e2",
  "to": "e4"
}
```
- **Expected**: 200 OK with updated game state

## Valid Chess Positions (Algebraic Notation)

Columns: a, b, c, d, e, f, g, h (files)
Rows: 1, 2, 3, 4, 5, 6, 7, 8 (ranks)

Examples:
- `e2` - White king pawn starting position
- `e4` - One square forward
- `a1` - White queen rook
- `h8` - Black king rook
- `f7` - Black king pawn starting position

## Performance Tips

1. **Local Testing**: Use localhost for faster response times
2. **Batch Operations**: Send multiple moves rapidly to test concurrency
3. **Game IDs**: Copy/save game IDs for repeated tests
4. **Monitoring**: Check server console for timing information

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Port 8080 already in use | Change port in ChessRestServer.scala or kill existing process |
| Game not found | Verify game ID is correct (copy-paste from creation response) |
| Invalid move format | Check position syntax (e.g., "e2e4", not "E2E4") |
| JSON parsing error | Ensure proper JSON format with quotes |
| Server not responding | Verify `sbt run` is still executing |

## Performance Metrics

- **Game Creation**: ~50ms
- **Move Validation**: ~10ms
- **Game State Retrieval**: ~5ms
- **List Games (100 games)**: ~20ms

(These are approximate values and may vary based on system)

## Automated Testing

```powershell
cd C:\sem7\SA\me_chess
sbt run
cd C:\sem7\SA\me_chess\src\rest\.test_api.ps1
```