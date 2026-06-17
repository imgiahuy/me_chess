#!/bin/bash
set -e

# Configuration
SERVER="chess@141.37.123.124"
PROJECT_DIR="~/chess"
REGISTRY="141.37.123.124:5000"
PASSWORD="chessPassWd#2026"

# Export password for sshpass
export SSHPASS="$PASSWORD"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== Deploying Chess Application to 141.37.123.124 ===${NC}"

# If kind cluster is running, tear it down first to free ports 80/443
echo -e "${GREEN}Checking for existing kind cluster...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH' 2>/dev/null || true
export PATH="$HOME/.local/bin:$PATH"
if command -v kind &>/dev/null && kind get clusters 2>/dev/null | grep -q "^chess$"; then
    echo "Tearing down kind cluster 'chess' to free ports..."
    kind delete cluster --name chess
    docker stop kind-registry 2>/dev/null || true
    sleep 3
fi
ENDSSH

# Check if sshpass is installed
if ! command -v sshpass &> /dev/null; then
    echo -e "${YELLOW}sshpass not found. Installing...${NC}"
    if [ "$(uname)" = "Darwin" ]; then
        brew install sshpass
    elif [ "$(uname)" = "Linux" ]; then
        sudo apt-get update && sudo apt-get install -y sshpass
    else
        echo -e "${RED}Please install sshpass manually${NC}"
        exit 1
    fi
fi

# Configure Docker to allow insecure registry (skip if Docker not available)
echo -e "${GREEN}Configuring Docker for insecure registry...${NC}"
if command -v docker &> /dev/null; then
    if [ "$(uname)" = "Darwin" ]; then
        # macOS
        echo '{"insecure-registries":["141.37.123.124:5000"]}' | sudo tee /etc/docker/daemon.json > /dev/null 2>&1 || \
        echo '{"insecure-registries":["141.37.123.124:5000"]}' > ~/.docker/daemon.json
        sudo killall Docker || true
        open -a Docker
    elif [ "$(uname)" = "Linux" ]; then
        # Linux
        sudo mkdir -p /etc/docker
        echo '{"insecure-registries":["141.37.123.124:5000"]}' | sudo tee /etc/docker/daemon.json > /dev/null
        sudo systemctl restart docker 2>/dev/null || sudo service docker restart 2>/dev/null || true
    fi
else
    echo -e "${YELLOW}Docker not found locally. Skipping local Docker configuration.${NC}"
    echo -e "${YELLOW}If using Docker Desktop on Windows, please manually add 141.37.123.124:5000 to insecure registries in Docker Desktop settings.${NC}"
fi

# Check if SSH connection works
echo -e "${GREEN}Testing SSH connection...${NC}"
if ! sshpass -e ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 $SERVER "echo 'Connection successful'"; then
    echo -e "${RED}Failed to connect to server. Please check SSH keys and network.${NC}"
    exit 1
fi

# Create project directory on server
echo -e "${GREEN}Creating project directory on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "mkdir -p $PROJECT_DIR"

# Copy files to server
echo -e "${GREEN}Copying files to server...${NC}"
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" \
    --exclude 'target' \
    --exclude 'node_modules' \
    --exclude '.git' \
    --exclude '.idea' \
    --exclude '.scala-build' \
    --exclude '.bsp' \
    --exclude 'gui' \
    --exclude 'tui' \
    ./ $SERVER:$PROJECT_DIR/

# Copy SSL certificates to server
echo -e "${GREEN}Copying SSL certificates to server...${NC}"
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" \
    docker/ssl/ $SERVER:$PROJECT_DIR/docker/ssl/

# Check if Docker is available on server
echo -e "${GREEN}Checking Docker on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'EOF'
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed on the server. Please ask the server admin to install Docker."
    exit 1
fi
if ! groups $USER | grep -q docker; then
    echo "ERROR: User is not in docker group. Please ask the server admin to run: sudo usermod -aG docker chess"
    exit 1
fi
EOF

# Check if images already exist on server
echo -e "${GREEN}Checking if images exist on server...${NC}"
REST_API_EXISTS=$(sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "docker images -q chess-rest-api:latest")
WEB_FRONTEND_EXISTS=$(sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "docker images -q chess-web-frontend:latest")

FORCE_BUILD=${1:-false}

# Delete existing images if they exist, then always rebuild
if [ -n "$REST_API_EXISTS" ] || [ -n "$WEB_FRONTEND_EXISTS" ]; then
    echo -e "${YELLOW}Images exist on server, deleting them...${NC}"
    sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "docker rmi chess-rest-api:latest chess-web-frontend:latest chess-spark:latest 2>/dev/null || true"
fi

echo -e "${GREEN}Building Docker images locally...${NC}"
docker build -t chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

# Build Spark analytics image (multi-stage Dockerfile assembles JAR internally)
echo -e "${GREEN}Building Spark analytics image...${NC}"
docker build -t chess-spark:latest -f spark/Dockerfile .

# Save images as tar files
echo -e "${GREEN}Saving Docker images as tar files...${NC}"
docker save chess-rest-api:latest -o chess-rest-api.tar
docker save chess-web-frontend:latest -o chess-web-frontend.tar
docker save chess-spark:latest -o chess-spark.tar

# Transfer tar files to server
echo -e "${GREEN}Transferring Docker images to server...${NC}"
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" chess-rest-api.tar $SERVER:$PROJECT_DIR/
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" chess-web-frontend.tar $SERVER:$PROJECT_DIR/
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" chess-spark.tar $SERVER:$PROJECT_DIR/

# Load images on server
echo -e "${GREEN}Loading Docker images on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "cd $PROJECT_DIR && docker load -i chess-rest-api.tar"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "cd $PROJECT_DIR && docker load -i chess-web-frontend.tar"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "cd $PROJECT_DIR && docker load -i chess-spark.tar"

# Clean up local tar files
echo -e "${GREEN}Cleaning up local tar files...${NC}"
rm chess-rest-api.tar chess-web-frontend.tar chess-spark.tar

# Clean up old images on server
echo -e "${GREEN}Cleaning up old images on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "docker image prune -f"

# Transfer docker-compose.prod.yml to server (use the repo file, not a generated one)
echo -e "${GREEN}Transferring docker-compose.prod.yml to server...${NC}"
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" docker-compose.prod.yml $SERVER:$PROJECT_DIR/

# Deploy with Docker Compose on server
echo -e "${GREEN}Deploying with Docker Compose on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << EOF
cd $PROJECT_DIR

# Stop existing containers and remove volumes to ensure clean initialization
docker compose -f docker-compose.prod.yml down -v 2>/dev/null || true

# Kill anything still holding port 80 or 443 (leftover from previous runs)
for PORT in 80 443; do
    PID=\$(sudo lsof -ti tcp:\$PORT 2>/dev/null || true)
    if [ -n "\$PID" ]; then
        echo "Killing process \$PID holding port \$PORT..."
        sudo kill -9 \$PID 2>/dev/null || true
    fi
done
sleep 2

# Start new containers
docker compose -f docker-compose.prod.yml up -d

# Wait for Keycloak to be healthy (max 3 minutes)
echo "Waiting for Keycloak to be healthy..."
for i in \$(seq 1 36); do
    if docker exec chess-keycloak curl -sf http://localhost:8080/auth/health/ready > /dev/null 2>&1; then
        echo "Keycloak is up!"
        break
    fi
    echo "  Attempt \$i/36 — waiting 5s..."
    sleep 5
done

# Disable SSL requirement on master and chess realms
echo "Disabling Keycloak SSL requirement..."
docker exec chess-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080/auth \
    --realm master \
    --user admin \
    --password admin
docker exec chess-keycloak /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=none
docker exec chess-keycloak /opt/keycloak/bin/kcadm.sh update realms/chess  -s sslRequired=none 2>/dev/null || true
echo "Keycloak SSL disabled."

# Wait for web-frontend to be reachable via nginx (max 3 minutes)
echo "Waiting for web frontend to be reachable..."
for i in \$(seq 1 36); do
    if curl -sf http://localhost/ > /dev/null 2>&1; then
        echo "Web frontend is up!"
        break
    fi
    echo "  Attempt \$i/36 — waiting 5s..."
    sleep 5
done
EOF

# Verify deployment
echo -e "${GREEN}Verifying deployment...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << EOF
cd $PROJECT_DIR
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=20
EOF

echo -e "${GREEN}=== Deployment complete! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://141.37.123.124"
echo -e "  - REST API:     http://141.37.123.124/v1"
echo -e "  - Keycloak:     http://141.37.123.124/auth"
echo -e "  - Kafka UI:     http://141.37.123.124/kafka-ui"
echo -e "${YELLOW}To view logs: ssh $SERVER 'cd $PROJECT_DIR && docker compose -f docker-compose.prod.yml logs -f'${NC}"
