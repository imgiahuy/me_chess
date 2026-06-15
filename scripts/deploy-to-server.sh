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

if [ "$FORCE_BUILD" = "force" ] || [ -z "$REST_API_EXISTS" ] || [ -z "$WEB_FRONTEND_EXISTS" ]; then
    if [ "$FORCE_BUILD" = "force" ]; then
        echo -e "${YELLOW}Force rebuild requested${NC}"
    else
        echo -e "${YELLOW}Images not found on server, building...${NC}"
    fi

    # Build images locally
    echo -e "${GREEN}Building Docker images locally...${NC}"
    docker build -t chess-rest-api:latest -f rest-api/Dockerfile .
    docker build -t chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

    # Save images as tar files
    echo -e "${GREEN}Saving Docker images as tar files...${NC}"
    docker save chess-rest-api:latest -o chess-rest-api.tar
    docker save chess-web-frontend:latest -o chess-web-frontend.tar

    # Transfer tar files to server
    echo -e "${GREEN}Transferring Docker images to server...${NC}"
    sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" chess-rest-api.tar $SERVER:$PROJECT_DIR/
    sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" chess-web-frontend.tar $SERVER:$PROJECT_DIR/

    # Load images on server
    echo -e "${GREEN}Loading Docker images on server...${NC}"
    sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "cd $PROJECT_DIR && docker load -i chess-rest-api.tar"
    sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "cd $PROJECT_DIR && docker load -i chess-web-frontend.tar"

    # Clean up local tar files
    echo -e "${GREEN}Cleaning up local tar files...${NC}"
    rm chess-rest-api.tar chess-web-frontend.tar

    # Clean up old images on server
    echo -e "${GREEN}Cleaning up old images on server...${NC}"
    sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "docker image prune -f"
else
    echo -e "${GREEN}Images already exist on server, skipping build${NC}"
    echo -e "${YELLOW}Use '$0 force' to force rebuild${NC}"
fi

# Create production docker-compose file locally
echo -e "${GREEN}Creating production docker-compose file...${NC}"
cat > docker-compose.prod.yml << 'YAML'
services:
  nginx-keycloak:
    image: nginx:alpine
    container_name: chess-nginx-keycloak
    ports:
      - "8081:80"
      - "8443:443"
    networks:
      - chess-network
    volumes:
      - ./docker/nginx-keycloak.conf:/etc/nginx/conf.d/default.conf
      - ./docker/ssl:/etc/nginx/ssl
    depends_on:
      - keycloak

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: chess-keycloak
    ports:
      - "8080:8080"
    networks:
      - chess-network
    environment:
      - KEYCLOAK_ADMIN=admin
      - KEYCLOAK_ADMIN_PASSWORD=admin
      - KC_DB=postgres
      - KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak
      - KC_DB_USERNAME=chess_user
      - KC_DB_PASSWORD=chess_password
      - KC_HOSTNAME=141.37.123.124
      - KC_HOSTNAME_PORT=8443
      - KC_HOSTNAME_STRICT=false
      - KC_HOSTNAME_STRICT_HTTPS=false
      - KC_HTTP_ENABLED=true
      - KC_PROXY=edge
    command: start-dev --import-realm
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    volumes:
      - ./docker/keycloak-realm.json:/opt/keycloak/data/import/keycloak-realm.json

  postgres:
    image: postgres:15
    container_name: chess-postgres
    ports:
      - "5432:5432"
    networks:
      - chess-network
    environment:
      - POSTGRES_DB=chess
      - POSTGRES_USER=chess_user
      - POSTGRES_PASSWORD=chess_password
      - POSTGRES_MULTIPLE_DATABASES=keycloak
    volumes:
      - chess-data:/var/lib/postgresql/data
      - ./docker/init-postgres.sh:/docker-entrypoint-initdb.d/init-postgres.sh
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chess_user -d chess"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:4.4
    container_name: chess-mongodb
    ports:
      - "27017:27017"
    networks:
      - chess-network
    environment:
      - MONGO_INITDB_DATABASE=chess
    volumes:
      - mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongo", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  rest-api:
    image: chess-rest-api:latest
    container_name: chess-rest-api
    ports:
      - "8085:8080"
    networks:
      - chess-network
    depends_on:
      postgres:
        condition: service_healthy
      mongodb:
        condition: service_healthy
    environment:
      - JAVA_OPTS=-Xmx512m
      - MONGODB_HOST=mongodb
      - MONGODB_PORT=27017
      - MONGODB_DATABASE=chess
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/v1/chess/info"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  web-frontend:
    image: chess-web-frontend:latest
    container_name: chess-web-frontend
    ports:
      - "3005:3000"
    networks:
      - chess-network
    depends_on:
      rest-api:
        condition: service_healthy
    environment:
      - REACT_APP_API_URL=http://rest-api:8080

networks:
  chess-network:
    driver: bridge

volumes:
  chess-data:
    driver: local
  mongo-data:
    driver: local
YAML

# Transfer docker-compose.prod.yml to server
echo -e "${GREEN}Transferring docker-compose.prod.yml to server...${NC}"
sshpass -e rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no" docker-compose.prod.yml $SERVER:$PROJECT_DIR/

# Deploy with Docker Compose on server
echo -e "${GREEN}Deploying with Docker Compose on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << EOF
cd $PROJECT_DIR

# Stop existing containers and remove volumes to ensure clean initialization
docker compose -f docker-compose.prod.yml down -v || true

# Start new containers
docker compose -f docker-compose.prod.yml up -d

# Wait for services to be healthy
echo "Waiting for services to be healthy..."
sleep 30
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
echo -e "  - Web Frontend: http://141.37.123.124:3005"
echo -e "  - REST API: http://141.37.123.124:8085"
echo -e "  - Keycloak (HTTPS): https://141.37.123.124:8443"
echo -e "  - Keycloak Admin: https://141.37.123.124:8443/admin"
echo -e "${YELLOW}Note: SSL certificate is self-signed. Your browser will show a security warning.${NC}"
echo -e "${YELLOW}To view logs: ssh $SERVER 'cd $PROJECT_DIR && docker compose -f docker-compose.prod.yml logs -f'${NC}"
