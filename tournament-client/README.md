# Tournament Client

A client library for integrating with the NowChess Tournament Server. This module enables chess bots to participate in centralized tournaments by handling authentication, tournament discovery, event streaming, and move submission.

## Features

- **Authentication**: Register bots and obtain JWT tokens for authenticated requests
- **Tournament Discovery**: List and query tournaments by status
- **Event Streaming**: Real-time NDJSON streams for tournament and game events
- **Move Computation**: Integration with local chess engine to compute UCI moves from FEN
- **Move Submission**: Submit moves to the tournament server with retry logic
- **Deduplication**: Prevent duplicate move submissions using state tracking
- **CLI Interface**: Command-line tool for bot registration and tournament participation

## Architecture

The tournament client follows the recommended architecture from the Tour-Houey integration plan:

### Components

- **TournamentApiClient**: HTTP client for the Tournament Server API
  - Handles bearer token authentication
  - Supports JSON, form-urlencoded, and NDJSON requests
  - Provides methods for all tournament endpoints

- **BotMoveAdapter**: Adapter for computing UCI moves
  - Accepts FEN, side-to-move, and assigned color
  - Calls the local chess engine or bot implementation
  - Returns UCI move strings (e.g., `e2e4` or `e7e8q`)

- **TournamentBotRunner**: Orchestrates tournament participation
  - Handles registration and joining
  - Manages tournament and game event streams
  - Computes and submits moves with deduplication
  - Includes retry logic and comprehensive logging

## Building

```bash
sbt tournament-client/compile
sbt tournament-client/assembly
```

The assembled JAR will be at: `tournament-client/target/scala-3.3.3/chess-tournament-client.jar`

## Usage

### CLI Commands

#### Register a Bot

```bash
java -jar chess-tournament-client.jar register MyBot --url http://localhost:8080
```

This will output a JWT token that you should save for future operations.

#### List Tournaments

```bash
java -jar chess-tournament-client.jar list --url http://localhost:8080
```

#### Join a Tournament

```bash
java -jar chess-tournament-client.jar join <tournament-id> --token <jwt-token> --url http://localhost:8080
```

#### Run in a Tournament (Auto-Register and Participate)

```bash
java -jar chess-tournament-client.jar run <tournament-id> --url http://localhost:8080 --bot-type stockfish
```

### Programmatic Usage

```scala
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import tournament.client.runner.TournamentBotRunner

implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "tournament-bot")
implicit val ec: ExecutionContext = system.executionContext

val runner = TournamentBotRunner(
  baseUrl = "http://localhost:8080",
  botName = "StockfishBot",
  botType = "stockfish"
)

// Register, join, and participate
for {
  _ <- runner.register()
  _ <- runner.joinTournament(tournamentId)
  _ <- runner.participate(tournamentId)
} yield {
  runner.shutdown()
  system.terminate()
}
```

### Using the API Client Directly

```scala
import tournament.client.api.TournamentApiClient

val apiClient = TournamentApiClient("http://localhost:8080")

// Register and get token
val response = await(apiClient.register(RegisterRequest("MyBot", isBot = true)))
apiClient.setToken(response.token)

// List tournaments
val tournaments = await(apiClient.listTournaments())

// Join a tournament
val tournament = await(apiClient.joinTournament(tournamentId))

// Open tournament stream
val stream = apiClient.openTournamentStream(tournamentId)
stream.runForeach { event =>
  // Handle tournament events
}
```

## Bot Types

The following bot types are supported:

- `stockfish`: Full strength Stockfish (depth 15, skill level 20)
- `stockfish-easy`: Easy Stockfish (depth 10, skill level 5)
- `stockfish-medium`: Medium Stockfish (depth 12, skill level 10)
- `random`: Random move selection
- `capture`: Greedy capture bot

## Environment Variables

- `TOURNAMENT_SERVER_URL`: Default tournament server URL (default: `http://localhost:8080`)
- `BOT_NAME`: Default bot name for registration
- `BOT_TYPE`: Default bot type (default: `stockfish`)
- `TOURNAMENT_TOKEN`: JWT token for authenticated requests

## Event Handling

### Tournament Events

The client handles the following tournament events from the NDJSON stream:

- `tournamentStarted`: Tournament has begun
- `roundStarted`: A new round has started
- `gameStart`: A game has been assigned to the bot
- `roundFinished`: A round has completed
- `tournamentFinished`: Tournament has ended
- `heartbeat`: Keep-alive (ignored)

### Game Events

The client handles the following game events from the NDJSON stream:

- `gameState`: Current game state with FEN and turn
- `move`: A move has been played
- `gameEnd`: Game has ended with result
- `heartbeat`: Keep-alive (ignored)

## Deduplication

The client uses deduplication to prevent duplicate move submissions:

- Tracks processed states using `(gameId + fen + turn)` keys
- Skips move computation for already-processed states
- Prevents race conditions in move submission

## Error Handling

The client includes comprehensive error handling:

- Retry logic for move submission (3 attempts with 1-second delay)
- Clear error logging for all failures
- Graceful handling of stream disconnections
- Validation of turn and assigned color before move computation

## Testing

Run the unit tests:

```bash
sbt tournament-client/test
```

## Integration with Tournament Server

This client is designed to work with the NowChess Tournament Server. Before using, ensure:

1. The Tournament Server is running and accessible
2. You have the correct server URL
3. The server's OpenAPI contract matches the expected endpoints
4. CORS is configured if the client runs from a different origin

## Security Notes

- Store JWT tokens securely. Browser `localStorage` is convenient but not secure on shared machines.
- Prefer HTTPS for deployed tournament operations.
- Never hardcode tokens in source code.
- Log every submitted move with tournament ID, game ID, FEN, assigned color, turn, and UCI for audit trails.

## Troubleshooting

### Connection Issues

If you cannot connect to the tournament server:

1. Verify the server URL is correct
2. Check if the server is running
3. Ensure no firewall is blocking the connection
4. Verify CORS settings if running from a browser

### Authentication Issues

If authentication fails:

1. Verify the bot name is unique
2. Check that the token is valid and not expired
3. Ensure the token is being sent in the `Authorization: Bearer <token>` header

### Move Submission Failures

If moves are not being accepted:

1. Check the server logs for specific error messages
2. Verify the UCI move format is correct
3. Ensure it's actually the bot's turn
4. Check that the game is still active

## License

This module is part of the me_chess project. See the main LICENSE file for details.
