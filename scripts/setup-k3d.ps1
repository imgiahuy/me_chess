# PowerShell script for Windows to set up k3d cluster

Write-Host "=== Setting up k3d cluster for Chess Application ===" -ForegroundColor Green

# Check if k3d is installed
if (-not (Get-Command k3d -ErrorAction SilentlyContinue)) {
    Write-Host "k3d not found. Please install from https://k3d.io/" -ForegroundColor Yellow
    Write-Host "Run: winget install k3d" -ForegroundColor Yellow
    exit 1
}

# Check if kubectl is installed
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    Write-Host "kubectl not found. Please install from https://kubernetes.io/docs/tasks/tools/" -ForegroundColor Yellow
    Write-Host "Run: winget install kubernetes.kubectl" -ForegroundColor Yellow
    exit 1
}

# Delete existing cluster if it exists
$existingCluster = k3d cluster list | Select-String "chess-cluster"
if ($existingCluster) {
    Write-Host "Deleting existing chess-cluster..." -ForegroundColor Yellow
    k3d cluster delete chess-cluster
}

# Create k3d cluster with port mappings
Write-Host "Creating k3d cluster..." -ForegroundColor Green
k3d cluster create chess-cluster `
    --api-port 127.0.0.1:60332 `
    --port "8080:80@loadbalancer" `
    --port "3000:3000@loadbalancer" `
    --port "8081:8081@loadbalancer" `
    --port "5432:5432@loadbalancer" `
    --port "27017:27017@loadbalancer" `
    --agents 2 `
    --wait

# Fix kubeconfig
Write-Host "Updating kubeconfig context..." -ForegroundColor Green
k3d kubeconfig merge chess-cluster --overwrite -s

# Verify cluster is ready
Write-Host "Verifying cluster is ready..." -ForegroundColor Green
kubectl get nodes

# Wait for cluster to be ready
Write-Host "Waiting for cluster to be ready..." -ForegroundColor Green
kubectl wait --for=condition=ready node --all --timeout=300s

# Install ingress-nginx if not already installed
$ingressNamespace = kubectl get namespace ingress-nginx --ignore-not-found
if (-not $ingressNamespace) {
    Write-Host "Installing ingress-nginx..." -ForegroundColor Green
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml
    kubectl wait --namespace ingress-nginx `
        --for=condition=ready pod `
        --selector=app.kubernetes.io/component=controller `
        --timeout=300s
}

# Create namespace
Write-Host "Creating chess namespace..." -ForegroundColor Green
kubectl apply -f k8s/namespace.yaml

# Build Docker images for k3d
Write-Host "Building Docker images for k3d..." -ForegroundColor Green
docker build -t chess-rest-api:latest -f rest-api/Dockerfile .
docker build -t chess-web-frontend:latest -f web/frontend/Dockerfile web/frontend

# Import images into k3d
Write-Host "Importing images into k3d..." -ForegroundColor Green
k3d image import chess-rest-api:latest -c chess-cluster
k3d image import chess-web-frontend:latest -c chess-cluster

# Deploy Kubernetes manifests
Write-Host "Deploying Kubernetes manifests..." -ForegroundColor Green
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/mongodb-deployment.yaml
kubectl apply -f k8s/keycloak-deployment.yaml
kubectl apply -f k8s/rest-api-deployment.yaml
kubectl apply -f k8s/web-frontend-deployment.yaml

# Wait for deployments to be ready
Write-Host "Waiting for deployments to be ready..." -ForegroundColor Green
kubectl wait --for=condition=available deployment/postgres -n chess --timeout=300s
kubectl wait --for=condition=available deployment/mongodb -n chess --timeout=300s
kubectl wait --for=condition=available deployment/keycloak -n chess --timeout=300s
kubectl wait --for=condition=available deployment/rest-api -n chess --timeout=300s
kubectl wait --for=condition=available deployment/web-frontend -n chess --timeout=300s

# Add chess.local to hosts file
Write-Host "Adding chess.local to hosts file..." -ForegroundColor Green
$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
try {
    $isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    
    if ($isAdmin) {
        $hostsContent = Get-Content $hostsPath
        if ($hostsContent -notmatch "chess.local") {
            Add-Content -Path $hostsPath -Value "127.0.0.1 chess.local"
            Write-Host "Added chess.local to hosts file." -ForegroundColor Green
        } else {
            Write-Host "chess.local already exists in hosts file." -ForegroundColor Green
        }
    } else {
        Write-Host "Not running as Administrator. Please manually add '127.0.0.1 chess.local' to:" -ForegroundColor Yellow
        Write-Host "  C:\Windows\System32\drivers\etc\hosts" -ForegroundColor Yellow
        Write-Host "Or run this script as Administrator." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Failed to modify hosts file. Please manually add '127.0.0.1 chess.local' to:" -ForegroundColor Yellow
    Write-Host "  C:\Windows\System32\drivers\etc\hosts" -ForegroundColor Yellow
}

Write-Host "=== Cluster setup complete! ===" -ForegroundColor Green
Write-Host "Access the application at:" -ForegroundColor Green
Write-Host "  - Web Frontend: http://chess.local:3000"
Write-Host "  - REST API: http://chess.local:8080"
Write-Host "  - Keycloak: http://chess.local:8081"
Write-Host "To view logs: kubectl logs -n chess -f deployment/<deployment-name>" -ForegroundColor Yellow
Write-Host "To delete cluster: k3d cluster delete chess-cluster" -ForegroundColor Yellow
