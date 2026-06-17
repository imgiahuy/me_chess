# Chess Application Deployment Guide

This guide covers deploying the Chess application using Docker Compose, Kubernetes with k3d (local), and Kubernetes with k3s (production server).

## Prerequisites

- Docker and Docker Compose installed
- For Kubernetes: kubectl installed
- For k3d: k3d installed (https://k3d.io/)
- SSH access to 141.37.123.124 for production deployment

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
- **Keycloak**: http://localhost:8081
- **Kafka UI**: http://localhost:8082
- **Kafka**: localhost:9092
- **PostgreSQL**: localhost:5432
- **MongoDB**: localhost:27017
- **Spark Analytics**: runs once on startup (batch mode), results in `spark-results` Docker volume

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

All services are routed through the nginx ingress on **port 80**:

- **Web Frontend**: http://chess.local
- **REST API**: http://chess.local/v1
- **Keycloak**: http://chess.local/auth
- **Kafka UI**: http://chess.local/kafka-ui

### View logs

```bash
kubectl logs -n chess -f deployment/rest-api
kubectl logs -n chess -f deployment/web-frontend
kubectl logs -n chess -f deployment/kafka
kubectl logs -n chess -f deployment/zookeeper
```

### Spark Analytics Job

The Spark batch job runs automatically on deploy. To view its output:

```bash
# Check job status
kubectl get jobs -n chess

# Stream logs from the spark pod
kubectl logs -n chess -l app=chess-spark --follow

# Re-run the job after it completes (delete + re-apply)
kubectl delete job chess-spark-batch -n chess
kubectl apply -k k8s/overlays/local   # or production

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
# Spark: multi-stage build, no pre-steps needed
docker build -t chess-spark:latest -f spark/Dockerfile .
```

### Spark Analytics Modes

**Batch mode** (default — reads `sample_games.pgn`, auto-started by `docker-compose up`):
```bash
docker run --rm -v $(pwd)/sample_games.pgn:/tmp/sample_games.pgn chess-spark:latest batch /tmp/chess-analytics
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
docker push 141.37.123.124:5000/chess-rest-api:latest
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

## Monitoring

### Check service health

```bash
# Docker Compose
docker-compose ps

# Kubernetes
kubectl get pods -n chess
kubectl get services -n chess
```

### Resource usage

```bash
# Docker stats
docker stats

# Kubernetes
kubectl top pods -n chess
kubectl top nodes
```
