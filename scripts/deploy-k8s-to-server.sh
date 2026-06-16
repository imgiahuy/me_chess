#!/bin/bash
set -e

# Configuration
SERVER="chess@141.37.123.124"
PROJECT_DIR="$HOME/chess-k8s"
REGISTRY="141.37.123.124:5000"
PASSWORD="chessPassWd#2026"

# Export password for sshpass
export SSHPASS="$PASSWORD"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== Deploying Chess Application with Kubernetes to 141.37.123.124 ===${NC}"
echo -e "${YELLOW}NOTE: Using rootless k3s (no sudo required)${NC}"

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

# Check if SSH connection works
echo -e "${GREEN}Testing SSH connection...${NC}"
if ! sshpass -e ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 $SERVER "echo 'Connection successful'"; then
    echo -e "${RED}Failed to connect to server. Please check credentials and network.${NC}"
    exit 1
fi

# Install kind and kubectl (binaries go to ~/.local/bin, no sudo needed)
echo -e "${GREEN}Installing kind and kubectl on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
mkdir -p ~/.local/bin
export PATH="$HOME/.local/bin:$PATH"

if ! command -v kind &>/dev/null; then
    echo "Downloading kind..."
    curl -sLo ~/.local/bin/kind https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64
    chmod +x ~/.local/bin/kind
fi

if ! command -v kubectl &>/dev/null; then
    echo "Downloading kubectl..."
    KUBE_VER=$(curl -sL https://dl.k8s.io/release/stable.txt)
    curl -sLo ~/.local/bin/kubectl "https://dl.k8s.io/release/${KUBE_VER}/bin/linux/amd64/kubectl"
    chmod +x ~/.local/bin/kubectl
fi

echo "kind: $(kind version)"
echo "kubectl: $(kubectl version --client --short 2>/dev/null || true)"
ENDSSH

# Stop Docker Compose deployment to free ports 80/443 before kind claims them
echo -e "${GREEN}Stopping existing Docker Compose deployment to free ports 80/443...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
COMPOSE_DIR="$HOME/chess"
if [ -f "${COMPOSE_DIR}/docker-compose.prod.yml" ]; then
    echo "Stopping docker-compose stack in ${COMPOSE_DIR}..."
    cd "${COMPOSE_DIR}"
    docker compose -f docker-compose.prod.yml down || docker-compose -f docker-compose.prod.yml down || true
elif [ -f "${COMPOSE_DIR}/docker-compose.yml" ]; then
    echo "Stopping docker-compose stack in ${COMPOSE_DIR}..."
    cd "${COMPOSE_DIR}"
    docker compose down || docker-compose down || true
else
    echo "No docker-compose project found at ${COMPOSE_DIR}, stopping named containers manually..."
    docker stop chess-nginx-keycloak chess-web-frontend chess-keycloak chess-rest-api chess-postgres chess-mongodb 2>/dev/null || true
fi
echo "Waiting for ports to be released..."
sleep 3
ENDSSH

# Create kind cluster with a local registry
echo -e "${GREEN}Creating kind cluster with local registry...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
export PATH="$HOME/.local/bin:$PATH"
export KUBECONFIG="$HOME/.kube/kind-config"
mkdir -p ~/.kube

REG_NAME="kind-registry"
REG_PORT="5001"
CLUSTER="chess"

# Start registry container if not running
if ! docker ps --format '{{.Names}}' | grep -q "^${REG_NAME}$"; then
    if docker ps -a --format '{{.Names}}' | grep -q "^${REG_NAME}$"; then
        docker start ${REG_NAME}
    else
        echo "Starting local registry on port ${REG_PORT}..."
        docker run -d --restart=always -p "127.0.0.1:${REG_PORT}:5000" --name "${REG_NAME}" registry:2
    fi
fi

# Create kind cluster if not already present
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER}$"; then
    echo "Creating kind cluster '${CLUSTER}'..."
    cat > /tmp/kind-config.yaml << KINDEOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${REG_PORT}"]
        endpoint = ["http://${REG_NAME}:5000"]
KINDEOF
    kind create cluster --name "${CLUSTER}" --config=/tmp/kind-config.yaml
    sleep 10
else
    echo "Kind cluster '${CLUSTER}' already exists"
fi

kind get kubeconfig --name "${CLUSTER}" > "$HOME/.kube/kind-config"
chmod 600 "$HOME/.kube/kind-config"

# Connect registry to kind network
docker network connect kind "${REG_NAME}" 2>/dev/null || true

# ConfigMap so tooling can discover the registry
kubectl apply -f - << REGCM
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${REG_PORT}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
REGCM

echo "Cluster nodes:"
kubectl get nodes
ENDSSH

# Create project directory on server (in home dir, no sudo needed)
echo -e "${GREEN}Creating project directory on server...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "mkdir -p ~/chess-k8s"

# Copy Kubernetes manifests to server
echo -e "${GREEN}Copying Kubernetes manifests to server...${NC}"
sshpass -e scp -o StrictHostKeyChecking=no k8s/*.yaml $SERVER:~/chess-k8s/

# Copy source to server and build natively (avoids Windows cross-platform issues)
echo -e "${GREEN}Copying source to server for native build...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER "mkdir -p ~/chess-src"

# Copy only what the Dockerfiles need
sshpass -e scp -o StrictHostKeyChecking=no -r \
    rest-api/Dockerfile \
    $SERVER:~/chess-src/

# For the rest-api we need the full build context (sbt project)
# Use tar to stream the project excluding heavy dirs
echo "  Streaming project source (excluding .git, target, node_modules)..."
tar --exclude='.git' \
    --exclude='*/target' \
    --exclude='node_modules' \
    --exclude='.scala-build' \
    -czf - . | sshpass -e ssh -o StrictHostKeyChecking=no $SERVER \
    'mkdir -p ~/chess-src/project && tar -xzf - -C ~/chess-src/project'

echo -e "${GREEN}Building images natively on server (no cache, always fresh)...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
export PATH="$HOME/.local/bin:$PATH"

echo "  Removing old images to force clean rebuild..."
docker rmi chess-rest-api:latest chess-web-frontend:latest 2>/dev/null || true

echo "  Building chess-rest-api..."
docker build --no-cache -t chess-rest-api:latest \
    -f ~/chess-src/project/rest-api/Dockerfile \
    ~/chess-src/project

echo "  Building chess-web-frontend..."
docker build --no-cache -t chess-web-frontend:latest \
    -f ~/chess-src/project/web/frontend/Dockerfile \
    ~/chess-src/project/web/frontend

echo "  Removing images from kind node to force re-load..."
# Delete the image from containerd inside the kind node so kind load always replaces it
docker exec chess-control-plane crictl rmi docker.io/library/chess-rest-api:latest 2>/dev/null || true
docker exec chess-control-plane crictl rmi docker.io/library/chess-web-frontend:latest 2>/dev/null || true

echo "  Loading fresh images into kind cluster..."
kind load docker-image chess-rest-api:latest --name chess
kind load docker-image chess-web-frontend:latest --name chess

echo "Images loaded into kind successfully."
ENDSSH

# Update Kubernetes manifests — use Never since images are pre-loaded into kind nodes
echo -e "${GREEN}Updating Kubernetes manifests...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
cd ~/chess-k8s
sed -i 's|imagePullPolicy: IfNotPresent|imagePullPolicy: Never|g' rest-api-deployment.yaml
sed -i 's|imagePullPolicy: IfNotPresent|imagePullPolicy: Never|g' web-frontend-deployment.yaml
ENDSSH

# Install ingress-nginx for kind
echo -e "${GREEN}Installing ingress-nginx for kind...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
export PATH="$HOME/.local/bin:$PATH"
export KUBECONFIG="$HOME/.kube/kind-config"

if ! kubectl get namespace ingress-nginx &>/dev/null; then
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/kind/deploy.yaml
    echo "Waiting for ingress-nginx controller..."
    kubectl wait --namespace ingress-nginx \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/component=controller \
        --timeout=300s
else
    echo "ingress-nginx already installed"
fi
ENDSSH

# Deploy Kubernetes manifests
echo -e "${GREEN}Deploying Kubernetes manifests...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
set -e
export PATH="$HOME/.local/bin:$PATH"
export KUBECONFIG="$HOME/.kube/kind-config"
cd ~/chess-k8s

kubectl apply -f namespace.yaml

kubectl apply -f postgres-deployment.yaml
kubectl apply -f mongodb-deployment.yaml

echo "Waiting for databases..."
kubectl wait --for=condition=available deployment/postgres -n chess --timeout=300s
kubectl wait --for=condition=available deployment/mongodb -n chess --timeout=300s

kubectl apply -f keycloak-realm-config.yaml

echo "Wiping keycloak DB so realm is re-imported fresh..."
kubectl delete deployment keycloak -n chess 2>/dev/null || true
kubectl exec -n chess deployment/postgres -- psql -U chess -c "DROP DATABASE IF EXISTS keycloak;" 2>/dev/null || true
kubectl exec -n chess deployment/postgres -- psql -U chess -c "CREATE DATABASE keycloak;" 2>/dev/null || true

kubectl apply -f keycloak-deployment.yaml
echo "Waiting for Keycloak..."
kubectl wait --for=condition=available deployment/keycloak -n chess --timeout=300s

echo "Disabling SSL requirement on master and chess realms..."
KC_POD=$(kubectl get pod -n chess -l app=keycloak -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n chess $KC_POD -- /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080/auth \
    --realm master \
    --user admin \
    --password admin123
kubectl exec -n chess $KC_POD -- /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=none
kubectl exec -n chess $KC_POD -- /opt/keycloak/bin/kcadm.sh update realms/chess  -s sslRequired=none 2>/dev/null || true
echo "SSL disabled on Keycloak realms."

kubectl apply -f rest-api-deployment.yaml
kubectl apply -f web-frontend-deployment.yaml

echo "Forcing rollout restart to pick up fresh images..."
kubectl rollout restart deployment/rest-api -n chess
kubectl rollout restart deployment/web-frontend -n chess

echo "Waiting for application..."
kubectl wait --for=condition=available deployment/rest-api -n chess --timeout=300s
kubectl wait --for=condition=available deployment/web-frontend -n chess --timeout=300s
ENDSSH

# Verify deployment
echo -e "${GREEN}Verifying deployment...${NC}"
sshpass -e ssh -o StrictHostKeyChecking=no $SERVER << 'ENDSSH'
export PATH="$HOME/.local/bin:$PATH"
export KUBECONFIG="$HOME/.kube/kind-config"
kubectl get all -n chess
kubectl get ingress -n chess
ENDSSH

echo -e "${GREEN}=== Kubernetes deployment complete! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://141.37.123.124"
echo -e "  - REST API:     http://141.37.123.124/v1"
echo -e "  - Keycloak:     http://141.37.123.124/auth"
echo -e "${YELLOW}To view logs:    ssh $SERVER 'export PATH=\$HOME/.local/bin:\$PATH KUBECONFIG=\$HOME/.kube/kind-config; kubectl logs -n chess -f deployment/<name>'${NC}"
echo -e "${YELLOW}To check status: ssh $SERVER 'export PATH=\$HOME/.local/bin:\$PATH KUBECONFIG=\$HOME/.kube/kind-config; kubectl get all -n chess'${NC}"
