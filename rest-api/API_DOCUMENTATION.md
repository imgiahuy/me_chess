# Chess REST API Documentation

## Overview

The ME Chess REST API provides a complete RESTful interface for managing chess games, making moves, and tracking game state. The API is built with Akka HTTP and uses JSON for all request/response payloads.

**Base URL**: `http://localhost:8080/v1/chess`

**API Version**: 1.0.0

---

## Features

- ✅ Full game lifecycle management (create, retrieve, delete)
- ✅ Move validation and application
- ✅ Game persistence (save/load)
- ✅ Comprehensive error handling
- ✅ CORS support for cross-origin requests
- ✅ Type-safe JSON serialization
- ✅ Game status and statistics endpoints

---

## Endpoints

### 1. Game Management

#### Create a New Game
```
POST /v1/chess/games
```

**Description**: Creates a new chess game session.

**Request Body**: None (empty POST)

**Response** (201 Created):
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Game created successfully"
}
```

**Error Response** (500 Internal Server Error):
```json
{
  "error": "Failed to create game: [error details]"
}
```

---

#### List All Games
```
GET /v1/chess/games
```

**Description**: Retrieves a list of all active game sessions.

**Response** (200 OK):
```json
{
  "games": [
    {
      "gameId": "550e8400-e29b-41d4-a716-446655440000",
      "turn": "White",
      "isGameOver": false,
      "moveCount": 5
    },
    {
      "gameId": "550e8400-e29b-41d4-a716-446655440001",
      "turn": "Black",
      "isGameOver": true,
      "moveCount": 42
    }
  ],
  "total": 2
}
```

**Error Response** (500 Internal Server Error):
```json
{
  "error": "Failed to list games: [error details]"
}
```

---

#### Get Game State
```
GET /v1/chess/games/{gameId}
```

**Description**: Retrieves the complete state of a specific game.

**Path Parameters**:
- `gameId` (string, required): The unique identifier of the game

**Response** (200 OK):
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "turn": "White",
  "moveHistory": ["e2e4", "c7c5"],
  "isGameOver": false,
  "winner": null
}
```

**Error Responses**:
- 404 Not Found: Game not found
- 400 Bad Request: Invalid game ID

---

#### Get Game Status
```
GET /v1/chess/games/{gameId}/status
```

**Description**: Retrieves a quick status summary of a game without full board state.

**Path Parameters**:
- `gameId` (string, required): The unique identifier of the game

**Response** (200 OK):
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "isGameOver": false,
  "winner": null,
  "turn": "White",
  "moveCount": 2
}
```

**Error Responses**:
- 404 Not Found: Game not found
- 400 Bad Request: Invalid game ID

---

#### Delete a Game
```
DELETE /v1/chess/games/{gameId}
```

**Description**: Deletes a game session from the server.

**Path Parameters**:
- `gameId` (string, required): The unique identifier of the game

**Response** (200 OK):
```json
{
  "message": "Game 550e8400-e29b-41d4-a716-446655440000 deleted successfully"
}
```

**Error Responses**:
- 404 Not Found: Game not found
- 400 Bad Request: Invalid game ID

---

### 2. Move Management

#### Apply a Move
```
POST /v1/chess/games/{gameId}/moves
```

**Description**: Applies a move to a game. The move must be valid according to chess rules.

**Path Parameters**:
- `gameId` (string, required): The unique identifier of the game

**Request Body**:
```json
{
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

**Request Fields**:
- `from` (string, required): Source square in algebraic notation (e.g., "e2")
- `to` (string, required): Destination square in algebraic notation (e.g., "e4")
- `promotion` (string, optional): Promotion piece for pawn promotions ("q", "r", "b", "n")

**Response** (200 OK):
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "turn": "Black",
  "moveHistory": ["e2e4"],
  "isGameOver": false,
  "winner": null
}
```

**Error Responses**:
- 400 Bad Request: Invalid move, empty request, or validation error
- 404 Not Found: Game not found
- Examples:
  ```json
  {
    "error": "'from' field cannot be empty"
  }
  ```
  ```json
  {
    "error": "Game is already over"
  }
  ```

---

### 3. Game Persistence

#### Save a Game
```
POST /v1/chess/games/{gameId}/save
```

**Description**: Saves the current state of a game to persistent storage.

**Path Parameters**:
- `gameId` (string, required): The unique identifier of the game

**Request Body**: None (empty POST)

**Response** (200 OK):
```json
{
  "message": "Game 550e8400-e29b-41d4-a716-446655440000 saved successfully"
}
```

**Error Responses**:
- 400 Bad Request: Game not found or save failed
- 404 Not Found: Game not found

---

#### Load a Game
```
POST /v1/chess/games/load
```

**Description**: Loads a previously saved game and creates a new session for it.

**Request Body**: None (empty POST)

**Response** (201 Created):
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440002",
  "message": "Game loaded successfully"
}
```

**Error Response** (400 Bad Request):
```json
{
  "error": "Failed to load game: [error details]"
}
```

---

### 4. API Information

#### Get API Info
```
GET /v1/chess/info
```

**Description**: Retrieves information about the API version and status.

**Response** (200 OK):
```json
{
  "name": "ME Chess REST API",
  "version": "1.0.0",
  "status": "running"
}
```

---

## Error Handling

All error responses follow a consistent format:

```json
{
  "error": "Description of what went wrong"
}
```

### HTTP Status Codes

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | Successful GET, POST, or DELETE |
| 201 | Created | Successful game creation or load |
| 400 | Bad Request | Invalid input, validation error, or game already over |
| 404 | Not Found | Game not found |
| 500 | Internal Server Error | Server-side error |

---

## Request/Response Examples

### Example 1: Create and Play a Game

**Step 1: Create a new game**
```bash
curl -X POST http://localhost:8080/v1/chess/games
```

Response:
```json
{
  "gameId": "game-123",
  "message": "Game created successfully"
}
```

**Step 2: Make a move**
```bash
curl -X POST http://localhost:8080/v1/chess/games/game-123/moves \
  -H "Content-Type: application/json" \
  -d '{"from": "e2", "to": "e4"}'
```

Response:
```json
{
  "gameId": "game-123",
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "turn": "Black",
  "moveHistory": ["e2e4"],
  "isGameOver": false,
  "winner": null
}
```

**Step 3: Check game status**
```bash
curl http://localhost:8080/v1/chess/games/game-123/status
```

Response:
```json
{
  "gameId": "game-123",
  "isGameOver": false,
  "winner": null,
  "turn": "Black",
  "moveCount": 1
}
```

---

### Example 2: Save and Load a Game

**Save the game**
```bash
curl -X POST http://localhost:8080/v1/chess/games/game-123/save
```

Response:
```json
{
  "message": "Game game-123 saved successfully"
}
```

**Load the game**
```bash
curl -X POST http://localhost:8080/v1/chess/games/load
```

Response:
```json
{
  "gameId": "game-124",
  "message": "Game loaded successfully"
}
```

---

## CORS Support

The API supports Cross-Origin Resource Sharing (CORS) for browser-based clients. The following headers are included in all responses:

- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS`
- `Access-Control-Allow-Headers: Content-Type, Authorization`
- `Access-Control-Max-Age: 3600`

---

## Validation Rules

### Game ID Validation
- Must not be empty
- Maximum length: 100 characters

### Move Request Validation
- `from` field must be exactly 2 characters (e.g., "e2")
- `to` field must be exactly 2 characters (e.g., "e4")
- Both fields are case-insensitive
- Promotion field is optional and case-insensitive

### Move Validation
- Move must be legal according to chess rules
- Cannot make moves on a completed game
- Move must be in valid UCI format (e.g., "e2e4", "e7e8q")

---

## Implementation Notes

- All timestamps are in UTC
- Game IDs are unique identifiers (UUIDs)
- FEN notation is used for board representation
- UCI notation is used for move representation
- The API is stateless; all game state is maintained server-side
- Concurrent requests are handled safely

---

## Rate Limiting

Currently, there are no rate limits on the API. This may change in future versions.

---

## Future Enhancements

- Authentication and authorization
- Rate limiting
- WebSocket support for real-time game updates
- Move suggestions and analysis
- Game history and statistics
- Player profiles and ratings

