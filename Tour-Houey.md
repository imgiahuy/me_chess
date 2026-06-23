# Tournament Server Integration Plan

## Summary

This document describes a general plan for integrating the NowChess Tournament Server into an existing chess project. It is intended for projects with a chess engine, bot logic, or playable chess API that now need to participate in centralized tournaments.

The source of truth for the tournament API is the Tournament Server repository:

- README: `/home/marvin/SA/tournament-server/README.md`
- OpenAPI contract: `/home/marvin/SA/tournament-server/api/openapi.yaml`

Before implementing against the server, always re-check the OpenAPI document. The API is documented as OpenAPI 3.x and the YAML file defines the current request/response shapes, authentication requirements, NDJSON stream events, tournament lifecycle endpoints, and game move endpoints.

## Integration Model

The Tournament Server owns tournaments, pairings, game state, clocks, move validation, and results. A chess project should integrate as a tournament client, not as the authority for tournament state.

The chess project is responsible for:

- registering or providing a bot identity
- joining or being added to a tournament
- listening to tournament and game event streams
- detecting when one of its bots is assigned to a game
- computing legal UCI moves from the current FEN
- submitting moves back to the Tournament Server

The Tournament Server is responsible for:

- creating and starting tournaments
- assigning games and colors
- enforcing whose turn it is
- validating submitted UCI moves
- publishing game/tournament NDJSON events
- producing standings and exports

## Recommended Architecture

Add a small Tournament Client module to the chess project. Keep this module separate from the chess engine and UI code.

Suggested components:

- `TournamentApiClient`
  - wraps HTTP calls to the Tournament Server
  - handles bearer tokens
  - supports JSON, form-urlencoded requests, and NDJSON streams

- `TournamentBotRunner`
  - orchestrates auth, join, stream handling, move computation, and move submission
  - owns retry/logging behavior
  - can run as a CLI worker, background service, or web-triggered task

- `BotMoveAdapter`
  - accepts FEN, side-to-move, assigned color, game id, and bot type/config
  - calls the local chess engine or bot implementation
  - returns a UCI move such as `e2e4` or `e7e8q`

- `TournamentWebConsole`
  - optional but strongly recommended
  - lets users register identities, create/list/join/start tournaments, open streams, inspect events, and submit or auto-submit moves from a browser

## Authentication Flow

The Tournament Server uses bearer tokens returned by:

```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "MyBot",
  "isBot": true
}
```

The response contains:

```json
{
  "id": "bot_or_user_id",
  "token": "jwt-token"
}
```

Use the token on authenticated endpoints:

```http
Authorization: Bearer <token>
```

Use two identities when the project also manages tournaments:

- director/user identity: `isBot=false`
  - creates tournaments
  - starts tournaments
  - adds registered bots by id

- bot identity: `isBot=true`
  - joins tournaments
  - streams assigned games
  - submits moves

Do not put bot identity in move request bodies. The Tournament Server derives the acting bot from the JWT subject.

## Tournament Discovery And Creation

To discover existing tournaments:

```http
GET /api/tournament
```

The response groups tournaments by status:

```json
{
  "created": [],
  "started": [],
  "finished": []
}
```

To create a tournament:

```http
POST /api/tournament
Authorization: Bearer <director-token>
Content-Type: application/x-www-form-urlencoded

name=Test&nbRounds=3&clockLimit=300&clockIncrement=0
```

The response contains the created tournament JSON, including its `id`. Store this id and use it for all later tournament endpoints.

Common form fields are documented in `api/openapi.yaml`, including:

- `name`
- `nbRounds`
- `clockLimit`
- `clockIncrement`
- `rated`
- `format`
- `startPosition`
- `matchesPerPairing`
- `groupSize`
- `opening`
- `bots`
- `maxConcurrentGames`

To start a tournament:

```http
POST /api/tournament/{id}/start
Authorization: Bearer <director-token>
```

## Bot Registration And Participation

There are two participation paths.

Direct bot join:

```http
POST /api/tournament/{id}/join
Authorization: Bearer <bot-token>
```

Registered bot flow:

```http
POST /api/bots
Authorization: Bearer <director-token>
Content-Type: application/json

{
  "name": "MyBot",
  "family": "heuristic",
  "strategyType": "greedy",
  "engineType": "internal",
  "modelVersion": "1"
}
```

Then add the registered bot to a tournament:

```http
POST /api/tournament/{id}/participants
Authorization: Bearer <director-token>
Content-Type: application/json

{
  "botId": "registered-bot-id"
}
```

For a first integration, direct bot join is usually simpler. The registered bot path is useful for tournament setup, analytics, and metadata.

## Streaming And Move Loop

Open the tournament stream before or around tournament start:

```http
GET /api/tournament/{id}/stream
Authorization: Bearer <bot-token>
Accept: application/x-ndjson
```

The stream returns newline-delimited JSON. Each line is one event.

Important tournament events:

- `tournamentStarted`
- `roundStarted`
- `gameStart`
- `roundFinished`
- `tournamentFinished`
- `heartbeat`

On a `gameStart` event, the client receives the game id and assigned color:

```json
{
  "type": "gameStart",
  "round": 1,
  "gameId": "abc123",
  "color": "white"
}
```

Then open the game stream:

```http
GET /api/tournament/{id}/game/{gameId}/stream
Authorization: Bearer <bot-token>
Accept: application/x-ndjson
```

Important game events:

- `gameState`
- `move`
- `gameEnd`
- `heartbeat`

Clients must ignore `heartbeat` events. They exist only to keep the connection alive.

For `gameState` and `move` events:

1. Read `fen`.
2. Read `turn`.
3. Compare `turn` with the assigned bot color from `gameStart`.
4. If it is not the bot's turn, do nothing.
5. If it is the bot's turn, compute a legal UCI move from the FEN.
6. Submit the move:

```http
POST /api/tournament/{id}/game/{gameId}/move/{uci}
Authorization: Bearer <bot-token>
```

Recommended safeguards:

- Deduplicate move decisions by `gameId + fen + turn`.
- Never submit a move when the assigned color is unknown.
- Ignore non-active game states such as pending or finished states.
- Treat server responses `400`, `403`, and `409` as useful feedback and log them clearly.
- Keep stream handling resilient to reconnects if the deployment environment is unstable.

## Web Console Recommendation

A web console is strongly recommended for tournament day operations, especially when direct shell access to the deployed app is inconvenient.

The console should support:

- configuring the Tournament Server base URL
- registering director and bot identities
- storing director and bot tokens locally in the browser
- creating tournaments through the form-urlencoded endpoint
- listing and selecting tournaments
- joining, withdrawing, and starting tournaments
- opening tournament and game NDJSON streams
- displaying heartbeats, game starts, game states, moves, and game ends
- manually submitting UCI moves
- computing a local bot move from the latest FEN
- optional auto-move mode that only submits when the assigned bot color is on turn

Important deployment note:

- If the console is served from a different origin than the Tournament Server, the Tournament Server must allow CORS for that origin, or the chess project must provide a same-origin backend proxy.

## Testing Checklist

Unit tests:

- decode `RegisterResponse`
- decode tournament list response
- decode `TournamentEvent`, including `heartbeat`
- decode `GameEvent`, including `heartbeat`
- compute a UCI move from a FEN
- skip move computation when it is not the bot's turn
- deduplicate repeated game states

Integration tests with a fake server/client:

- register director
- create tournament
- register bot
- join tournament
- start tournament
- receive `gameStart`
- receive `gameState`
- compute move
- submit move
- ignore heartbeat events

Manual smoke test:

1. Start the Tournament Server.
2. Start the chess project API.
3. Open the web console.
4. Register director and bot identities.
5. Create a tournament.
6. Join with the bot.
7. Start the tournament.
8. Open tournament stream.
9. Confirm `gameStart` creates or selects a game id.
10. Open game stream.
11. Confirm FEN updates.
12. Compute and submit a move.
13. Verify the server accepts the move.

## Minimal Implementation Order

1. Read `/home/marvin/SA/tournament-server/README.md`.
2. Read `/home/marvin/SA/tournament-server/api/openapi.yaml`.
3. Implement auth registration and bearer token handling.
4. Implement tournament list/create/get/start/join endpoints.
5. Implement NDJSON stream reader with heartbeat ignoring.
6. Implement game-state-to-UCI move adapter.
7. Implement move submission.
8. Add deduplication and assigned-color checks.
9. Add web console for operational control.
10. Add tests and a manual tournament-day smoke test.

## Operational Notes

- Store tokens carefully. Browser `localStorage` is convenient but not secure on shared machines.
- Prefer HTTPS for deployed tournament operations.
- Log every submitted move with tournament id, game id, FEN hash or FEN, assigned color, turn, and UCI.
- Keep the OpenAPI YAML close during implementation; it is the contract to trust when README and code comments are less detailed.
