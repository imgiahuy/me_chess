[![Coverage Status](https://coveralls.io/repos/github/imgiahuy/me_chess/badge.svg?branch=main&dummy=1)](https://coveralls.io/github/imgiahuy/me_chess?branch=main)

# Chess Project

A comprehensive chess application built with Scala, featuring clean architecture, microservices, and multiple user interfaces. The project demonstrates modern software engineering practices including domain-driven design, event-driven architecture, and containerized deployment.

## Features

### Core Chess Engine
- **Complete chess rules implementation**: Move validation, check/checkmate detection, castling, en passant, pawn promotion
- **Multiple bot opponents**: Random, greedy, capture, aggressive, and Stockfish (UCI engine integration)
- **Time control support**: Classical, rapid, blitz with configurable time and increment
- **Game state management**: Pause/resume, resignation, timeout detection
- **PGN export/import**: Standard chess notation support

### User Interfaces
- **Web Frontend**: React 19 + TypeScript + Vite with modern UI
- **REST API**: Akka HTTP-based API with rate limiting
- **Terminal UI (TUI)**: Interactive command-line interface
- **Graphical UI (GUI)**: ScalaFX desktop application (Linux/macOS with X11)

### Microservices Architecture
- **Player Service**: Standalone player management with MongoDB
- **Tournament Service**: Tournament management and bot evaluation arena
- **Lichess Integration Service**: Connects to Lichess API for online play
- **Auth Service**: Keycloak integration for authentication

### Data & Analytics
- **Multi-database support**: PostgreSQL, MongoDB, H2 (in-memory)
- **Redis caching**: Performance optimization for game and player data
- **Apache Spark**: Game analytics and statistics
- **Kafka event streaming**: Real-time game event publishing

### Deployment
- **Docker Compose**: Local development environment
- **Kubernetes**: Production deployment with Helm-style overlays
- **Health checks**: Comprehensive monitoring for all services

## Architecture

The project follows **Clean Architecture** principles with clear separation of concerns:

### Layer Structure

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ REST API │  │   Web    │  │   TUI    │  │   GUI    │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Use Cases│  │Controller │  │ Services │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Models  │  │  Engine  │  │  Parser  │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│              Infrastructure Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │Database  │  │  Redis   │  │  Kafka   │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

### Module Structure

**Core Layer (Domain)**
- `core`: Pure chess domain models (Board, Piece, Move, PositionState)
- `shared`: Common utilities and types

**Application Layer**
- `application`: Use cases, controllers, services, engines, parsers
- `domain-persistence`: Repository and DAO interfaces

**Infrastructure Layer**
- `infrastructure-persistence`: Concrete database implementations (Slick, MongoDB, Redis)
- `player-service`: Player management microservice
- `tournament-service`: Tournament management microservice
- `lichess-integration-service`: Lichess API integration
- `auth-service`: Keycloak authentication service

**Presentation Layer**
- `rest-api`: HTTP REST API endpoints
- `web/frontend`: React web application
- `tui`: Terminal user interface
- `gui`: ScalaFX graphical interface

**Analytics & Testing**
- `spark`: Apache Spark analytics
- `benchmark`: JMH performance benchmarks
- `tournament-client`: External tournament server client

## Getting Started

### Prerequisites

- **Java**: JDK 21 or higher
- **Scala**: 3.3.3 (managed by sbt)
- **Docker**: For containerized services
- **Docker Compose**: For local development
- **sbt**: Scala build tool

### Quick Start with Docker Compose

1. **Clone the repository**
```bash
git clone https://github.com/imgiahuy/me_chess.git
cd me_chess
```

2. **Start all services**
```bash
docker-compose up -d
```

This starts:
- PostgreSQL (database)
- MongoDB (player data)
- Redis (caching)
- Kafka (event streaming)
- Keycloak (authentication)
- REST API (port 8085)
- Web Frontend (port 3005)
- Player Service (port 8090)
- Tournament Service (port 8070)

3. **Access the application**
- Web UI: http://localhost:3005
- REST API: http://localhost:8085/v1/chess/info
- Keycloak Admin: http://localhost:8081/admin (admin/admin)

### Build from Source

1. **Build all modules**
```bash
sbt compile
```

2. **Run tests**
```bash
sbt test
```

3. **Build Docker images**
```bash
sbt docker:publishLocal
```

4. **Run specific interfaces**

**Web Frontend**
```bash
cd web/frontend
npm install
npm run dev
```

**Terminal UI**
```bash
sbt "project tui" run
```

**REST API**
```bash
sbt "project rest-api" run
```

## Development Guide

### Project Structure

```
me_chess/
├── core/                      # Domain models
│   └── src/main/scala/model/
├── application/               # Business logic
│   ├── src/main/scala/
│   │   ├── controller/       # Game controllers
│   │   ├── service/          # Game services (ChessBotService, GameService)
│   │   ├── usecase/          # Use cases (CreateGameUseCase, MakeMoveUseCase)
│   │   ├── engine/           # UCI engine integration
│   │   ├── parser/           # Move parsers (UCI, FEN, PGN)
│   │   └── formatter/        # Formatters
├── infrastructure-persistence/ # Database implementations
│   ├── src/main/scala/
│   │   ├── dao/             # Data access objects
│   │   ├── repository/      # Repository implementations
│   │   ├── slick/           # Slick (PostgreSQL, H2)
│   │   ├── mongodb/         # MongoDB
│   │   └── redis/           # Redis caching
├── rest-api/                 # REST API
│   └── src/main/scala/api/
├── web/frontend/             # React frontend
├── player-service/           # Player microservice
├── tournament-service/       # Tournament microservice
├── lichess-integration-service/ # Lichess integration
├── auth-service/             # Authentication service
├── k8s/                      # Kubernetes manifests
├── docker-compose.yml        # Local development
└── build.sbt                 # Build configuration
```

### Key Concepts

**ChessBotService**
- Central service for bot move calculation
- Supports internal bots (random, greedy, capture) and UCI engines (Stockfish)
- Caches bot instances for performance

**GameService**
- Pure chess game logic
- Move validation and application
- Game state management (check, checkmate, stalemate)
- Time control handling

**Repository Pattern**
- Interface in `domain-persistence`
- Implementations in `infrastructure-persistence`
- Supports multiple backends (PostgreSQL, MongoDB, H2)

**Event-Driven Architecture**
- Kafka publishes game events (move made, game ended, etc.)
- Services subscribe to events for analytics and notifications

### Adding a New Bot

1. **Create bot class in core**
```scala
// core/src/main/scala/model/MyBot.scala
class MyBot extends Bot {
  override def name: String = "My Bot"
  override def selectMove(state: PositionState, moves: List[Move]): Move = {
    // Your logic here
    moves.head
  }
}
```

2. **Register in BotFactory**
```scala
// core/src/main/scala/model/BotFactory.scala
case "my-bot" => new MyBot()
```

3. **Add to available bots**
```scala
// application/src/main/scala/service/ChessBotService.scala
BotFactory.availableBots ++ List("my-bot")
```

### Running Tests

```bash
# Run all tests
sbt test

# Run specific module tests
sbt "project core" test
sbt "project application" test

# Run with coverage
sbt clean coverage test coverageReport
```

### Code Style

The project follows Scala best practices:
- Immutable data structures
- Pure functions where possible
- Either/Result for error handling
- Dependency injection
- Clean architecture layering

## Configuration

### Environment Variables

**Database**
- `MONGODB_HOST`: MongoDB host (default: localhost)
- `MONGODB_PORT`: MongoDB port (default: 27017)
- `MONGODB_DATABASE`: Database name (default: chess)
- `REDIS_HOST`: Redis host (default: localhost)
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_ENABLED`: Enable Redis caching (default: false)

**Kafka**
- `KAFKA_ENABLED`: Enable Kafka (default: false)
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka servers (default: localhost:9092)

**Services**
- `PLAYER_SERVICE_URL`: Player service URL (default: http://localhost:8090)
- `AUTH_SERVICE_URL`: Auth service URL (default: http://localhost:8088)

**Lichess Integration**
- `LICHESS_API_TOKEN`: Lichess bot API token
- `BOT_TYPE`: Default bot type (default: greedy)

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment instructions including:
- Docker Compose setup
- Kubernetes deployment
- Production configuration
- Troubleshooting

## Roadmap

- [ ] Improve move validation performance
- [ ] Add more bot AI algorithms (Minimax, Alpha-Beta)
- [ ] Implement puzzle mode
- [ ] Add game analysis features
- [ ] Improve mobile responsiveness
- [ ] Add multiplayer via WebSockets
- [ ] Implement ELO rating system
- [ ] Add opening book integration
- [ ] Improve test coverage

## Contributing

This project is developed with AI assistance to explore clean architecture and modern software engineering practices. Contributions are welcome!

## License

See LICENSE file for details.
