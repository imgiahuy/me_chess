#!/bin/bash
set -e

# Configuration
SERVER="root@141.37.123.124"
PROJECT_DIR="/opt/chess-k8s"
REGISTRY="141.37.123.124:5000"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== Deploying Chess Application with Kubernetes to 141.37.123.124 ===${NC}"

# Check if SSH connection works
echo -e "${GREEN}Testing SSH connection...${NC}"
if ! ssh -o ConnectTimeout=10 $SERVER "echo 'Connection successful'"; then
    echo -e "${RED}Failed to connect to server. Please check SSH keys and network.${NC}"
    exit 1
fi

# Install Kubernetes on server (k3s for simplicity)
echo -e "${GREEN}Installing k3s on server...${NC}"
ssh $SERVER << 'EOF'
if ! command -v k3s &> /dev/null; then
    curl -sfL https://get.k3s.io | sh -
    systemctl start k3s
    systemctl enable k3s
    
    # Wait for k3s to be ready
    sleep 10
    
    # Install kubectl
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x kubectl
    mv kubectl /usr/local/bin/
    
    # Grant kubectl access to root
    mkdir -p /root/.kube
    cp /etc/rancher/k3s/k3s.yaml /root/.kube/config
    chmod 600 /root/.kube/config
fi
EOF

# Copy kubeconfig to local machine
echo -e "${GREEN}Copying kubeconfig from server...${NC}"
scp $SERVER:/etc/rancher/k3s/k3s.yaml ./kubeconfig-chess-server
sed -i 's/127.0.0.1/141.37.123.124/g' ./kubeconfig-chess-server
export KUBECONFIG=./kubeconfig-chess-server

# Create project directory on server
echo -e "${GREEN}Creating project directory on server...${NC}"
ssh $SERVER "mkdir -p $PROJECT_DIR"

# Copy Kubernetes manifests to server
echo -e "${GREEN}Copying Kubernetes manifests to server...${NC}"
scp k8s/*.yaml $SERVER:$PROJECT_DIR/

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

# Configure containerd to use insecure registry
mkdir -p /etc/rancher/k3s
cat > /etc/rancher/k3s/registries.yaml << 'REGISTRY'
mirrors:
  "141.37.123.124:5000":
    endpoint:
      - "http://141.37.123.124:5000"
configs:
  "141.37.123.124:5000":
    tls:
      insecure_skip_verify: true
REGISTRY

# Restart k3s to apply registry configuration
systemctl restart k3s
sleep 10
EOF

# Build and push images
echo -e "${GREEN}Building and pushing Docker images...${NC}"
docker build -t $REGISTRY/chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t $REGISTRY/chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

# Push images to server registry
echo -e "${GREEN}Pushing images to server registry...${NC}"
docker push $REGISTRY/chess-rest-api:latest
docker push $REGISTRY/chess-web-frontend:latest

# Update Kubernetes manifests to use local registry
echo -e "${GREEN}Updating Kubernetes manifests for local registry...${NC}"
ssh $SERVER << EOF
cd $PROJECT_DIR
sed -i 's|image: chess-rest-api:latest|image: $REGISTRY/chess-rest-api:latest|g' rest-api-deployment.yaml
sed -i 's|image: chess-web-frontend:latest|image: $REGISTRY/chess-web-frontend:latest|g' web-frontend-deployment.yaml
sed -i 's|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g' rest-api-deployment.yaml
sed -i 's|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g' web-frontend-deployment.yaml
EOF

# Deploy Kubernetes manifests
echo -e "${GREEN}Deploying Kubernetes manifests...${NC}"
ssh $SERVER << 'EOF'
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# Create namespace
kubectl apply -f namespace.yaml

# Deploy databases
kubectl apply -f postgres-deployment.yaml
kubectl apply -f mongodb-deployment.yaml

# Wait for databases
kubectl wait --for=condition=available deployment/postgres -n chess --timeout=300s
kubectl wait --for=condition=available deployment/mongodb -n chess --timeout=300s

# Deploy Keycloak
kubectl apply -f keycloak-deployment.yaml
kubectl wait --for=condition=available deployment/keycloak -n chess --timeout=300s

# Deploy application
kubectl apply -f rest-api-deployment.yaml
kubectl apply -f web-frontend-deployment.yaml

# Wait for application
kubectl wait --for=condition=available deployment/rest-api -n chess --timeout=300s
kubectl wait --for=condition=available deployment/web-frontend -n chess --timeout=300s
EOF

# Install ingress-nginx on server
echo -e "${GREEN}Installing ingress-nginx on server...${NC}"
ssh $SERVER << 'EOF'
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

if ! kubectl get namespace ingress-nginx &> /dev/null; then
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml
    kubectl wait --namespace ingress-nginx \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/component=controller \
        --timeout=300s
fi
EOF

# Configure firewall
echo -e "${GREEN}Configuring firewall...${NC}"
ssh $SERVER << 'EOF'
if command -v ufw &> /dev/null; then
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw allow 8080/tcp
    ufw allow 3000/tcp
    ufw allow 8081/tcp
elif command -v firewall-cmd &> /dev/null; then
    firewall-cmd --permanent --add-port=80/tcp
    firewall-cmd --permanent --add-port=443/tcp
    firewall-cmd --permanent --add-port=8080/tcp
    firewall-cmd --permanent --add-port=3000/tcp
    firewall-cmd --permanent --add-port=8081/tcp
    firewall-cmd --reload
fi
EOF

# Verify deployment
echo -e "${GREEN}Verifying deployment...${NC}"
ssh $SERVER << 'EOF'
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get all -n chess
kubectl get ingress -n chess
EOF

echo -e "${GREEN}=== Kubernetes deployment complete! ===${NC}"
echo -e "${GREEN}Access the application at:${NC}"
echo -e "  - Web Frontend: http://141.37.123.124"
echo -e "  - REST API: http://141.37.123.124/v1"
echo -e "  - Keycloak: http://141.37.123.124/auth"
echo -e "${YELLOW}To view logs: ssh $SERVER 'kubectl logs -n chess -f deployment/<deployment-name>'${NC}"
echo -e "${YELLOW}To manage cluster: ssh $SERVER 'kubectl get nodes -n chess'${NC}"
