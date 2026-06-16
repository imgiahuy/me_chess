# Chess Application Deployment Guide

This guide covers deploying the Chess application using Docker Compose, Kubernetes with k3d (local), and Kubernetes with k3s (production server).

## Prerequisites

- Docker and Docker Compose installed
- For Kubernetes: kubectl installed
- For k3d: k3d installed (https://k3d.io/)
- SSH access to 141.37.123.124 for production deployment

## Local Development with Docker Compose

### Start all services

```bash
docker-compose up -d
```

### Services exposed on localhost

- **Web Frontend**: http://localhost:3005
- **REST API**: http://localhost:8085
- **Keycloak**: http://localhost:8081
- **PostgreSQL**: localhost:5432
- **MongoDB**: localhost:27017

### Keycloak Configuration

- **Admin Console**: http://localhost:8081/admin
- **Username**: admin
- **Password**: admin
- **Realm**: chess
- **Test User**: admin / admin123

### Stop services

```bash
docker-compose down
```

### Clean up volumes

```bash
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

- **Web Frontend**: http://chess.local:3000
- **REST API**: http://chess.local:8080
- **Keycloak**: http://chess.local:8081

### View logs

```bash
kubectl logs -n chess -f deployment/rest-api
kubectl logs -n chess -f deployment/web-frontend
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

**Kubernetes (via ingress on port 80):**
- **Web Frontend**: http://141.37.123.124
- **REST API**: http://141.37.123.124/v1
- **Keycloak**: http://141.37.123.124/auth

### SSH to server for management

```bash
ssh chess@141.37.123.124
```

### View logs on server

**Docker Compose:**
```bash
cd /opt/chess
docker-compose logs -f
```

**Kubernetes:**
```bash
kubectl logs -n chess -f deployment/rest-api
kubectl get all -n chess
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
