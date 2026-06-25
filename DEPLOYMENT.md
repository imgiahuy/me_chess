# Chess Application Deployment Guide

This guide covers deploying the Chess application using Docker Compose, Kubernetes with k3d (local), and Kubernetes with k3s (production server).

## Prerequisites

- Docker and Docker Compose installed
- For Kubernetes: kubectl installed
- For k3d: k3d installed (https://k3d.io/)
- SSH access to 141.37.123.124 for production deployment
- Scala 3.3.3 (for local builds)
- Node.js 18+ (for frontend builds)

## Local Development with Docker Compose

### Start all services (with Kafka)

```bash
docker-compose -f docker-compose.yml -f docker-compose.kafka.yml up -d
```

### Start without Kafka

```bash
docker-compose up -d
```

### Services exposed on localhost

- **Web Frontend**: http://localhost:3005
- **REST API**: http://localhost:8085
- **Player Service**: http://localhost:8090
- **Tournament Service**: http://localhost:8070
- **Keycloak**: http://localhost:8081
- **Kafka UI**: http://localhost:8082
- **Kafka**: localhost:9092
- **PostgreSQL**: localhost:5432
- **MongoDB**: localhost:27017
- **Redis**: localhost:6379
- **Spark Analytics**: runs once on startup (batch mode), then scheduler refreshes every 5 minutes, results in `spark-results` Docker volume

### Player Service

The `player-service` is a standalone microservice that manages player profiles and statistics. It stores player data in MongoDB and provides REST endpoints for player management.

**API endpoints:**
- `GET /v1/players` — list all players
- `GET /v1/players/{id}` — get player by ID
- `POST /v1/players` — create a new player
- `PUT /v1/players/{id}` — update player
- `DELETE /v1/players/{id}` — delete player
- `GET /v1/players/health` — health check

### Tournament Service

The `tournament-service` manages chess tournaments and bot evaluation arenas. It supports round-robin, Swiss, and arena formats and persists data to MongoDB.

**API endpoints:**
- `POST /v1/tournaments` — create a tournament
- `GET /v1/tournaments` — list tournaments
- `POST /v1/tournaments/{id}/register` — register a participant
- `POST /v1/tournaments/{id}/start` — start the tournament
- `POST /v1/tournaments/{id}/result` — report a game result
- `GET /v1/tournaments/{id}/standings` — get standings
- `GET /v1/tournaments/{id}/pairings` — get pairings

**Kafka topics:**
- `chess-tournament-created`
- `chess-tournament-started`
- `chess-tournament-finished`
- `chess-tournament-participant-registered`
- `chess-tournament-result-reported`

**Web UI:** navigate to `/tournaments` from the main menu.

### Lichess Bot Service

The `lichess-integration-service` connects to Lichess via the Bot API and plays games using the internal chess engine. It is optional and requires a Lichess **BOT account** token.

**Create a Lichess BOT account:**
1. Register a dedicated account at https://lichess.org.
2. Enable the Bot API at https://lichess.org/account/oauth/create (scope: `challenge:read`, `challenge:write`, `bot:play`, `game:read`, `game:write`).
3. Copy the generated token.

**Docker Compose:**

```bash
export LICHESS_API_TOKEN="your_lichess_bot_token"
# Optional: choose a different bot engine
export BOT_TYPE="greedy"  # or random / capture

docker-compose up -d lichess-integration-service
```

**Kubernetes:**

```bash
# Update the secret value
kubectl patch secret lichess-secret -n chess --type=string -p='{"stringData":{"LICHESS_API_TOKEN":"your_lichess_bot_token"}}'
kubectl rollout restart deployment/lichess-integration-service -n chess
```

**Configuration options (env vars):**

| Variable | Default | Description |
|---|---|---|
| `LICHESS_API_TOKEN` | (required) | Lichess BOT account API token |
| `BOT_TYPE` | `greedy` | Internal bot engine used for moves |
| `ALLOW_RATED` | `true` | Accept rated challenges |
| `ALLOW_UNRATED` | `true` | Accept unrated challenges |
| `ALLOWED_SPEEDS` | `bullet,blitz,rapid,classical` | Comma-separated accepted time controls |
| `ALLOWED_VARIANTS` | `standard` | Comma-separated accepted variants |
| `MAX_INITIAL_TIME_SECONDS` | `2147483647` | Maximum initial time in seconds |
| `MIN_INCREMENT_SECONDS` | `0` | Minimum increment in seconds |

**View bot logs:**

```bash
# Docker Compose
docker-compose logs -f lichess-integration-service

# Kubernetes
kubectl logs -n chess -f deployment/lichess-integration-service
```

### Keycloak Configuration

- **Admin Console**: http://localhost:8081/admin
- **Username**: admin
- **Password**: admin
- **Realm**: chess
- **Test User**: admin / admin123

### Kafka Configuration

Kafka is an event streaming platform that enables microservices to communicate asynchronously. The chess application uses Kafka to publish game events (game created, moves made, game ended, etc.) which can be consumed by other services.

**Kafka Topics:**
- `chess-events` - General chess game events
- `chess-game-created` - New game creation events
- `chess-move-made` - Chess move events
- `chess-game-ended` - Game conclusion events
- `chess-player-resigned` - Player resignation events
- `chess-time-events` - Time warning and timeout events
- `chess-state-updates` - Game state update events

**Verify Kafka is working:**
1. Open Kafka UI at http://localhost:8082
2. Create a game via the web UI
3. Check the `chess-game-created` topic in Kafka UI
4. You should see a new message with the game creation event

**Kafka Environment Variables:**
- `KAFKA_ENABLED=true` - Enables Kafka publishing in REST API
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092` - Kafka broker address (Docker Compose)
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092` - Kafka broker address (Kubernetes)

### Stop services

```bash
# With Kafka
docker-compose -f docker-compose.yml -f docker-compose.kafka.yml down

# Without Kafka
docker-compose down
```

### Clean up volumes

```bash
# With Kafka
docker-compose -f docker-compose.yml -f docker-compose.kafka.yml down -v

# Without Kafka
docker-compose down -v
```

## Local Development with Kubernetes (k3d)

### Windows (PowerShell)

```powershell
.\scripts\setup-k3d.ps1
```

### Linux/Mac (Bash)

```bash
chmod +x scripts/setup-k3d.sh
./scripts/setup-k3d.sh
```

### Access the application

All services are routed through the Traefik ingress (built into k3d) on **port 80**:

- **Web Frontend**: http://chess.local
- **REST API**: http://chess.local/v1
- **Player Service**: http://chess.local/v1/players
- **Tournament Service**: http://chess.local/v1/tournaments
- **Keycloak**: http://chess.local/auth
- **Kafka UI**: http://chess.local/kafka-ui

### View logs

```bash
kubectl logs -n chess -f deployment/rest-api
kubectl logs -n chess -f deployment/web-frontend
kubectl logs -n chess -f deployment/player-service
kubectl logs -n chess -f deployment/tournament-service
kubectl logs -n chess -f deployment/bot-service
kubectl logs -n chess -f deployment/kafka
kubectl logs -n chess -f deployment/zookeeper
kubectl logs -n chess -f deployment/redis
```

### Spark Analytics Job

The Spark analytics job runs as a CronJob every 5 minutes in Kubernetes, processing game data from MongoDB. To view its output:

```bash
# Check CronJob status
kubectl get cronjob -n chess

# Check job history
kubectl get jobs -n chess

# Stream logs from the latest spark job pod
kubectl logs -n chess -l app=chess-spark --follow --tail=100

# Manually trigger a one-off job
kubectl create job chess-spark-manual --from=cronjob/chess-spark-analytics -n chess

# Run in Kafka streaming mode instead (one-off pod)
kubectl run chess-spark-kafka \
  --image=chess-spark:latest \
  --restart=Never \
  -n chess \
  -- kafka kafka:29092 /tmp/streaming-results
```

### Delete cluster

```bash
k3d cluster delete chess-cluster
```

## Production Deployment (141.37.123.124)

### Option 1: Docker Compose Deployment

```bash
chmod +x scripts/deploy-to-server.sh
./scripts/deploy-to-server.sh
```

This will:
1. Copy project files to server
2. Set up Docker registry on server
3. Build and push Docker images
4. Deploy with Docker Compose

### Option 2: Kubernetes Deployment (k3s)

Note: This option is currently not fully implemented. The project uses k3d for local development and Docker Compose for production.

```bash
chmod +x scripts/deploy-k8s-to-server.sh
./scripts/deploy-k8s-to-server.sh
```

This will:
1. Install k3s on server
2. Set up Docker registry
3. Deploy all services with Kubernetes
4. Configure ingress-nginx
5. Open firewall ports

### Access the production application

**Docker Compose:**
- **Web Frontend**: http://141.37.123.124:3005
- **REST API**: http://141.37.123.124:8085
- **Keycloak**: http://141.37.123.124:8081
- **Kafka UI**: http://141.37.123.124:8082

**Kubernetes (via ingress on port 80):**
- **Web Frontend**: http://141.37.123.124
- **REST API**: http://141.37.123.124/v1
- **Keycloak**: http://141.37.123.124/auth
- **Kafka UI**: http://141.37.123.124/kafka-ui

### SSH to server for management

```bash
ssh chess@141.37.123.124
```

### View logs on server

**Docker Compose:**
```bash
cd /opt/chess
docker-compose -f docker-compose.yml -f docker-compose.kafka.yml logs -f
```

**Kubernetes:**
```bash
kubectl logs -n chess -f deployment/rest-api
kubectl logs -n chess -f deployment/kafka
kubectl get all -n chess
```

## Kubernetes Environment Configuration (Kustomize)

Kubernetes manifests use [Kustomize](https://kustomize.io/) overlays to separate local and production config. No hardcoded IPs exist in the base manifests.

```
k8s/
  base/                  # environment-agnostic base manifests
    - namespace.yaml
    - secrets.yaml
    - postgres-deployment.yaml
    - mongodb-deployment.yaml
    - redis-deployment.yaml
    - keycloak-deployment.yaml
    - keycloak-realm-config.yaml
    - kafka-deployment.yaml
    - kafka-ui-deployment.yaml
    - player-service-deployment.yaml
    - lichess-integration-service-deployment.yaml
    - tournament-service-deployment.yaml
    - rest-api-deployment.yaml
    - rest-api-hpa.yaml
    - web-frontend-deployment.yaml
    - spark-job.yaml
    - ingress.yaml
    - pod-disruption-budgets.yaml
  overlays/
    local/               # chess.local hostnames, used by setup-k3d scripts
    production/          # 141.37.123.124 hostnames, used by deploy-k8s-to-server.sh
```

### Apply locally (k3d)

```bash
kubectl apply -k k8s/overlays/local
```

### Apply to production (k3s)

```bash
kubectl apply -k k8s/overlays/production
```

## Switching Between Docker and Kubernetes

The two deployments share the same server but compete for ports 80 and 443. Use these scripts to switch cleanly:

### Switch to Docker Compose (from Kubernetes)

```bash
chmod +x scripts/switch-to-docker.sh
./scripts/switch-to-docker.sh
```

This stops and deletes the kind cluster, then restarts the Docker Compose stack. Data volumes are preserved.

### Switch to Kubernetes (from Docker Compose)

```bash
./scripts/deploy-k8s-to-server.sh
```

This automatically stops the Docker Compose stack before creating the kind cluster.

---

## Docker Images

### Build images locally

```bash
docker build -t chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend
docker build -t chess-player-service:latest -f player-service/Dockerfile .
docker build -t chess-lichess-integration-service:latest -f lichess-integration-service/Dockerfile .
docker build -t chess-tournament-service:latest -f tournament-service/Dockerfile .
# Spark: multi-stage build, no pre-steps needed
docker build -t chess-spark:latest -f spark/Dockerfile .
```

### Spark Analytics Modes

**MongoDB mode** (default — reads from MongoDB, auto-started by `docker-compose up`):
```bash
docker run --rm --network chess-network chess-spark:latest mongo
```

**File mode** (pass your own PGN or JSON):
```bash
docker run --rm -v /path/to/games.pgn:/data/games.pgn chess-spark:latest file /data/games.pgn /tmp/results
```

**Kafka streaming mode** (connects to running Kafka):
```bash
docker run --rm --network chess-network chess-spark:latest kafka kafka:29092 /tmp/streaming-results
```

### Push to registry

```bash
docker tag chess-rest-api:latest 141.37.123.124:5000/chess-rest-api:latest
docker tag chess-web-frontend:latest 141.37.123.124:5000/chess-web-frontend:latest
docker tag chess-player-service:latest 141.37.123.124:5000/chess-player-service:latest
docker tag chess-lichess-integration-service:latest 141.37.123.124:5000/chess-lichess-integration-service:latest
docker tag chess-tournament-service:latest 141.37.123.124:5000/chess-tournament-service:latest
docker tag chess-spark:latest 141.37.123.124:5000/chess-spark:latest
docker push 141.37.123.124:5000/chess-rest-api:latest
docker push 141.37.123.124:5000/chess-web-frontend:latest
docker push 141.37.123.124:5000/chess-player-service:latest
docker push 141.37.123.124:5000/chess-lichess-integration-service:latest
docker push 141.37.123.124:5000/chess-tournament-service:latest
docker push 141.37.123.124:5000/chess-spark:latest
```

## Troubleshooting

### Docker Compose issues

```bash
# View logs
docker-compose logs

# Restart specific service
docker-compose restart rest-api

# Rebuild service
docker-compose up -d --build rest-api
```

### Kubernetes issues

```bash
# Check pod status
kubectl get pods -n chess

# Describe pod for errors
kubectl describe pod <pod-name> -n chess

# View logs
kubectl logs <pod-name> -n chess

# Restart deployment
kubectl rollout restart deployment/rest-api -n chess
```

### Keycloak issues

If Keycloak fails to start, check PostgreSQL connection:
```bash
docker-compose logs postgres
docker-compose logs keycloak
```

### Database connection issues

Ensure databases are healthy before starting application:
```bash
docker-compose ps
```

### Redis connection issues

Check Redis is running:
```bash
docker-compose logs redis
docker exec chess-redis redis-cli ping
```

### Kafka issues

**Check Kafka is running:**
```bash
docker-compose ps kafka zookeeper
```

**View Kafka logs:**
```bash
docker-compose logs kafka
docker-compose logs zookeeper
```

**Check Kafka topics:**
```bash
docker exec chess-kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Test Kafka producer:**
```bash
# Create a game via web UI and check if event appears in Kafka UI
# Or use console consumer:
docker exec chess-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic chess-game-created --from-beginning --max-messages 1
```

**Kubernetes:**
```bash
kubectl get pods -n chess | grep kafka
kubectl logs -n chess deployment/kafka
kubectl logs -n chess deployment/zookeeper
```

## Security Notes

- Change default passwords in production
- Use environment variables for secrets
- Enable HTTPS for production
- Configure proper firewall rules
- Use secrets management for Kubernetes
- The project includes Kubernetes secrets in `k8s/base/secrets.yaml` for sensitive data like Lichess API tokens
- GUI and TUI services are commented out in Docker Compose as they are desktop-only applications requiring X11 forwarding

         ## Performance Testing

The project includes multiple performance-testing tools to verify the REST API and core chess engine.

### Gatling (SBT)

Simulations are located under `rest-api/src/test/scala/performance/` and use the shared `BaseChessSimulation` class with CSV feeders.

Run the default simulation:

```bash
sbt "restApi/GatlingIt/testOnly performance.ChessApiSimulation"
```

Run the smoke test:

```bash
sbt "restApi/GatlingIt/testOnly performance.SmokeChessSimulation"
```

Run the load test:

```bash
sbt "restApi/GatlingIt/testOnly performance.LoadChessSimulation"
```

Run the stress test:

```bash
sbt "restApi/GatlingIt/testOnly performance.StressChessSimulation"
```

Run the soak test:

```bash
sbt "restApi/GatlingIt/testOnly performance.SoakChessSimulation"
```

Run the end-to-end test:

```bash
sbt "restApi/GatlingIt/testOnly performance.EndToEndChessSimulation"
```

Run the capacity test (finds server max limit/breaking point):

```bash
sbt "restApi/GatlingIt/testOnly performance.CapacitySimulation"
```

The capacity test ramps up to 300 concurrent users over 10 minutes to identify the server's breaking point. Monitor the Gatling HTML report to see when response times degrade or error rates increase.

Override the base URL via Java property:

```bash
sbt -Dgatling.baseUrl=http://localhost:8085/v1/chess "restApi/GatlingIt/testOnly performance.LoadChessSimulation"
```

### k6

k6 scripts are located under `k6/` and cover smoke, load, and stress profiles.

```bash
# Smoke test
k6 run k6/smoke.js

# Load test
k6 run k6/load.js

# Stress test
k6 run k6/stress.js
```

Override the target URL:

```bash
k6 run -e BASE_URL=http://localhost:8085/v1/chess k6/load.js
```

### JMH Microbenchmarks

JMH benchmarks are located under `benchmark/src/main/scala/chess/benchmark/` and run via the `sbt-jmh` plugin.

```bash
sbt benchmark/Jmh/run
```

### Run All Performance Tests

Run every Gatling simulation, k6 script, and JMH benchmark in sequence:

```bash
# All Gatling simulations
sbt "restApi/GatlingIt/testOnly performance.SmokeChessSimulation" \
    "restApi/GatlingIt/testOnly performance.LoadChessSimulation" \
    "restApi/GatlingIt/testOnly performance.StressChessSimulation" \
    "restApi/GatlingIt/testOnly performance.SoakChessSimulation" \
    "restApi/GatlingIt/testOnly performance.EndToEndChessSimulation" \
    "restApi/GatlingIt/testOnly performance.CapacitySimulation"

# All k6 scripts
k6 run k6/smoke.js && k6 run k6/load.js && k6 run k6/stress.js

# JMH microbenchmarks
sbt benchmark/Jmh/run
```

## Monitoring

### Check service health

```bash
# Docker Compose
docker-compose ps

# Kubernetes
kubectl get pods -n chess
kubectl get services -n chess
kubectl get hpa -n chess
kubectl get pdb -n chess
```

### Resource usage

```bash
# Docker stats
docker stats

# Kubernetes
kubectl top pods -n chess
kubectl top nodes
```

## Project Architecture

The chess application follows a modular architecture with clear separation of concerns:

### Core Modules
- **core**: Pure domain logic (models, analysis, openings) - no dependencies on other modules
- **application**: Services, controllers, use cases, engines - depends on core and domainPersistence
- **domainPersistence**: Repository and DAO interfaces - depends on core and shared
- **infrastructurePersistence**: Concrete implementations (InMemoryGameRepository, DatabaseGameRepository, DAOs) - depends on core, domainPersistence, and shared
- **shared**: Shared utilities and types

### Presentation Layer
- **rest-api**: Http4s REST API endpoints with Gatling performance testing
- **web-frontend**: React 19 + TypeScript + Vite frontend with React Router
- **gui**: ScalaFX graphical user interface (desktop-only, requires X11 forwarding)
- **tui**: Terminal user interface (desktop-only)

### Microservices
- **player-service**: Standalone Akka HTTP-based player management service
- **lichess-integration-service**: Lichess Bot API integration service
- **tournament-service**: Tournament management and bot evaluation arena service
- **tournament-client**: Client for external NowChess Tournament Server

### Analytics
- **spark**: Apache Spark 3.5.1 analytics for game data analysis (uses Scala 2.13 artifacts for compatibility)
- **benchmark**: JMH microbenchmarks for performance testing

### Legacy
- **persistent**: Deprecated module being replaced by infrastructure-persistence

### Technology Stack
- **Backend**: Scala 3.3.3, Akka HTTP, Http4s
- **Frontend**: React 19, TypeScript, Vite, React Router
- **Databases**: PostgreSQL 15, MongoDB 7, Redis 7
- **Message Broker**: Apache Kafka 7.5.0 with Confluent Platform
- **Authentication**: Keycloak 24.0
- **Analytics**: Apache Spark 3.5.1
- **Containerization**: Docker, Docker Compose
- **Orchestration**: Kubernetes with k3d (local) and k3s (production)
- **Testing**: Gatling, k6, JMH, ScalaTest
