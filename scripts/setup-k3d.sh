#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Setting up k3d cluster for Chess Application ===${NC}"

# Check if k3d is installed
if ! command -v k3d &> /dev/null; then
    echo -e "${YELLOW}k3d not found. Installing...${NC}"
    curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${YELLOW}kubectl not found. Installing...${NC}"
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x kubectl
    sudo mv kubectl /usr/local/bin/
fi

# Delete existing cluster if it exists
if k3d cluster list | grep -q "chess-cluster"; then
    echo -e "${YELLOW}Deleting existing chess-cluster...${NC}"
    k3d cluster delete chess-cluster
fi

# Create k3d cluster with port mappings
echo -e "${GREEN}Creating k3d cluster...${NC}"
k3d cluster create chess-cluster \
    --port "80:80@loadbalancer" \
    --agents 2 \
    --wait

# Wait for cluster to be ready
echo -e "${GREEN}Waiting for cluster to be ready...${NC}"
kubectl wait --for=condition=ready node --all --timeout=300s

# Note: k3d includes Traefik as the built-in ingress controller on port 80.
# No separate ingress-nginx installation needed.

# Create namespace
echo -e "${GREEN}Creating chess namespace...${NC}"
kubectl apply -f k8s/namespace.yaml

# Build Docker images for k3d
echo -e "${GREEN}Building Docker images for k3d...${NC}"
docker build -t chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

# Build Spark analytics image (multi-stage Dockerfile assembles JAR internally)
echo -e "${GREEN}Building Spark analytics image...${NC}"
docker build -t chess-spark:latest -f spark/Dockerfile .

# Import images into k3d
echo -e "${GREEN}Importing images into k3d...${NC}"
k3d image import chess-rest-api:latest -c chess-cluster
k3d image import chess-web-frontend:latest -c chess-cluster
k3d image import chess-spark:latest -c chess-cluster

# Deploy Kubernetes manifests (local overlay with chess.local hostnames)
echo -e "${GREEN}Deploying Kubernetes manifests (local overlay)...${NC}"
kubectl apply -k k8s/overlays/local

# Wait for deployments to be ready
echo -e "${GREEN}Waiting for deployments to be ready...${NC}"
kubectl wait --for=condition=available deployment/postgres -n chess --timeout=300s
kubectl wait --for=condition=available deployment/mongodb -n chess --timeout=300s
kubectl wait --for=condition=available deployment/zookeeper -n chess --timeout=300s
kubectl wait --for=condition=available deployment/kafka -n chess --timeout=300s
kubectl wait --for=condition=available deployment/keycloak -n chess --timeout=300s
kubectl wait --for=condition=available deployment/rest-api -n chess --timeout=300s
kubectl wait --for=condition=available deployment/web-frontend -n chess --timeout=300s

# Add chess.local to /etc/hosts
echo -e "${GREEN}Adding chess.local to /etc/hosts...${NC}"
if ! grep -q "chess.local" /etc/hosts; then
    echo "127.0.0.1 chess.local" | sudo tee -a /etc/hosts
fi

echo -e "${GREEN}=== Cluster setup complete! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://chess.local"
echo -e "  - REST API:     http://chess.local/v1"
echo -e "  - Keycloak:     http://chess.local/auth"
echo -e "  - Kafka UI:     http://chess.local/kafka-ui"
echo -e "${YELLOW}To view logs: kubectl logs -n chess -f deployment/<deployment-name>${NC}"
echo -e "${YELLOW}To delete cluster: k3d cluster delete chess-cluster${NC}"
