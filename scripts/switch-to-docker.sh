#!/bin/bash
set -e

# Configuration
SERVER="chess@141.37.123.124"
PASSWORD="chessPassWd#2026"

export SSHPASS="$PASSWORD"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Switching from Kubernetes (kind) to Docker Compose ===${NC}"

# Check if SSH connection works
echo -e "${GREEN}Testing SSH connection...${NC}"
if ! sshpass -e ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 $SERVER "echo 'Connection successful'"; then
    echo -e "${RED}Failed to connect to server.${NC}"
    exit 1
fi

# Stop and delete the kind cluster to free ports 80/443
echo -e "${GREEN}Tearing down kind cluster...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
export PATH="$HOME/.local/bin:$PATH"

if command -v kind &>/dev/null && kind get clusters 2>/dev/null | grep -q "^chess$"; then
    echo "Deleting kind cluster 'chess'..."
    kind delete cluster --name chess
else
    echo "No kind cluster named 'chess' found, skipping."
fi

# Stop kind-registry (keep it available for future k8s deploys)
if docker ps --format '{{.Names}}' | grep -q "^kind-registry$"; then
    echo "Stopping kind-registry..."
    docker stop kind-registry
fi

echo "Waiting for ports to be released..."
sleep 3
ENDSSH

# Start Docker Compose stack
echo -e "${GREEN}Starting Docker Compose stack...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
COMPOSE_DIR="$HOME/chess"
if [ ! -d "$COMPOSE_DIR" ]; then
    echo "ERROR: Docker Compose project not found at $COMPOSE_DIR"
    echo "Run ./scripts/deploy-to-server.sh first to deploy with Docker."
    exit 1
fi

cd "$COMPOSE_DIR"

if [ -f "docker-compose.prod.yml" ]; then
    echo "Starting with docker-compose.prod.yml..."
    docker compose -f docker-compose.prod.yml up -d || docker-compose -f docker-compose.prod.yml up -d
else
    echo "Starting with docker-compose.yml..."
    docker compose up -d || docker-compose up -d
fi

echo ""
echo "Running containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
ENDSSH

echo -e "${GREEN}=== Switched to Docker Compose! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://141.37.123.124:3005"
echo -e "  - REST API:     http://141.37.123.124:8085"
echo -e "  - Keycloak:     http://141.37.123.124:8081"
echo -e "${YELLOW}To switch back to Kubernetes: ./scripts/deploy-k8s-to-server.sh${NC}"
