#!/bin/bash
set -e

# Configuration
SERVER="root@141.37.74.144"
PROJECT_DIR="/opt/chess"
REGISTRY="141.37.74.144:5000"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== Deploying Chess Application to 141.37.74.144 ===${NC}"

# Check if SSH connection works
echo -e "${GREEN}Testing SSH connection...${NC}"
if ! ssh -o ConnectTimeout=10 $SERVER "echo 'Connection successful'"; then
    echo -e "${RED}Failed to connect to server. Please check SSH keys and network.${NC}"
    exit 1
fi

# Create project directory on server
echo -e "${GREEN}Creating project directory on server...${NC}"
ssh $SERVER "mkdir -p $PROJECT_DIR"

# Copy files to server
echo -e "${GREEN}Copying files to server...${NC}"
rsync -avz --progress \
    --exclude 'target' \
    --exclude 'node_modules' \
    --exclude '.git' \
    --exclude '.idea' \
    --exclude '.scala-build' \
    --exclude '.bsp' \
    --exclude 'gui' \
    --exclude 'tui' \
    ./ $SERVER:$PROJECT_DIR/

# Setup Docker registry on server
echo -e "${GREEN}Setting up Docker registry on server...${NC}"
ssh $SERVER << 'EOF'
# Install Docker if not present
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    systemctl start docker
    systemctl enable docker
fi

# Start Docker registry if not running
if ! docker ps | grep -q registry; then
    docker run -d -p 5000:5000 --name registry registry:2
fi
EOF

# Build and push images
echo -e "${GREEN}Building and pushing Docker images...${NC}"
docker build -t $REGISTRY/chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t $REGISTRY/chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

# Push images to server registry
echo -e "${GREEN}Pushing images to server registry...${NC}"
docker push $REGISTRY/chess-rest-api:latest
docker push $REGISTRY/chess-web-frontend:latest

# Pull images on server
echo -e "${GREEN}Pulling images on server...${NC}"
ssh $SERVER "docker pull $REGISTRY/chess-rest-api:latest"
ssh $SERVER "docker pull $REGISTRY/chess-web-frontend:latest"

# Deploy with Docker Compose on server
echo -e "${GREEN}Deploying with Docker Compose on server...${NC}"
ssh $SERVER << EOF
cd $PROJECT_DIR

# Update docker-compose.yml to use local registry
sed -i 's|image: chess-rest-api:latest|image: $REGISTRY/chess-rest-api:latest|g' docker-compose.yml
sed -i 's|image: chess-web-frontend:latest|image: $REGISTRY/chess-web-frontend:latest|g' docker-compose.yml

# Stop existing containers
docker-compose down || true

# Start new containers
docker-compose up -d

# Wait for services to be healthy
echo "Waiting for services to be healthy..."
sleep 30
EOF

# Verify deployment
echo -e "${GREEN}Verifying deployment...${NC}"
ssh $SERVER << 'EOF'
cd /opt/chess
docker-compose ps
docker-compose logs --tail=20
EOF

echo -e "${GREEN}=== Deployment complete! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://141.37.74.144:3005"
echo -e "  - REST API: http://141.37.74.144:8085"
echo -e "  - Keycloak: http://141.37.74.144:8081"
echo -e "${YELLOW}To view logs: ssh $SERVER 'cd $PROJECT_DIR && docker-compose logs -f'${NC}"
