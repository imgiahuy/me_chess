# k3d Local Deployment — Bug Fixes & Study Notes

This document records every bug encountered while setting up the Chess application on a local Kubernetes cluster using k3d. Each bug is explained from first principles so it is useful as a learning reference.

---

## Background: How k3d Works

Before diving into bugs, it helps to understand the traffic flow in k3d:

```
Your Browser
     |
     | http://chess.local  (resolves to 127.0.0.1 via /etc/hosts)
     v
[Host machine: port 80]
     |
     | Docker port mapping: --port "80:80@loadbalancer"
     v
[k3d loadbalancer container]  <-- a Docker container acting as a load balancer
     |
     | forwards to port 80 on all k3s nodes
     v
[k3s node containers]  <-- the actual Kubernetes nodes (also Docker containers)
     |
     | Ingress Controller (e.g. Traefik) listens on port 80
     v
[Ingress resource]  <-- routing rules: which path goes to which Service
     |
     v
[Kubernetes Service]  <-- ClusterIP, finds the right pod
     |
     v
[Pod / Container]  <-- your actual application
```

An **Ingress Controller** is the component inside the cluster that reads your `Ingress` YAML rules and actually routes HTTP traffic to the right service. k3d ships with **Traefik** as its built-in ingress controller.

---

## Bug 1 — `ERR_EMPTY_RESPONSE` on all URLs

### Symptom

Opening `http://chess.local` in the browser shows:

```
Diese Seite funktioniert nicht
chess.local hat keine Daten gesendet.
ERR_EMPTY_RESPONSE
```

This means the browser successfully made a TCP connection, but received zero bytes back — the server accepted the connection then sent nothing.

### What Was Happening

The setup script was installing `ingress-nginx` with this command:

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/
    controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml
#                                                    ^^^^^
#                                              "cloud" provider!
```

The `cloud` provider manifest creates the ingress-nginx controller service as type **`LoadBalancer`**:

```
kubectl get svc -n ingress-nginx

NAME                       TYPE           CLUSTER-IP     EXTERNAL-IP   PORT(S)
ingress-nginx-controller   LoadBalancer   10.43.x.x      <pending>     80:30366/TCP
#                                                        ^^^^^^^^^
#                                              Never gets an IP in k3d!
```

**Why `<pending>` causes the bug:**

In a real cloud (AWS EKS, GCP GKE, etc.), when you create a `LoadBalancer` service, the cloud provider automatically provisions a real load balancer and assigns it a public IP. Kubernetes then sets `EXTERNAL-IP` to that IP, and traffic flows.

In k3d, there is no cloud provider. Nothing assigns the IP. The service stays `<pending>` forever. The k3d loadbalancer container on your host has port 80 mapped, but it doesn't know where to forward traffic to because the service has no IP. So it accepts TCP connections and then drops them → `ERR_EMPTY_RESPONSE`.

**The second problem — k3d already has Traefik:**

k3d bundles k3s, and k3s bundles Traefik as its default ingress controller. Traefik was already running and wired to port 80 on all nodes:

```
kubectl get svc -n kube-system

NAME      TYPE           CLUSTER-IP    EXTERNAL-IP                          PORT(S)
traefik   LoadBalancer   10.43.x.x     172.20.0.3,172.20.0.4,172.20.0.5   80:31400/TCP
#                                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
#                                      k3d automatically assigns IPs to THIS one
```

Traefik had working IPs, was wired to port 80, and was ready to route traffic. Installing ingress-nginx on top was completely unnecessary.

### Fix

**1. Remove ingress-nginx installation from the setup scripts.**

`scripts/setup-k3d.ps1` and `scripts/setup-k3d.sh` — before:
```bash
# WRONG: installs ingress-nginx which will never get an IP in k3d
kubectl apply -f https://.../provider/cloud/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod ...
```

After (removed entirely, replaced with a comment):
```bash
# Note: k3d includes Traefik as the built-in ingress controller on port 80.
# No separate ingress-nginx installation needed.
```

**2. Change the cluster port mapping to only expose port 80.**

Before:
```bash
k3d cluster create chess-cluster \
    --port "8080:80@loadbalancer" \   # host:8080 → cluster:80
    --port "3000:3000@loadbalancer" \ # these ports had no listeners!
    --port "8081:8081@loadbalancer"   # these ports had no listeners!
```

After:
```bash
k3d cluster create chess-cluster \
    --port "80:80@loadbalancer"       # host:80 → cluster:80 (Traefik)
```

All services are now accessed through a single port (80) via path-based routing.

### Lesson

> **k3d bundles Traefik.** Always run `kubectl get svc -n kube-system` before installing any ingress controller. If Traefik is already there with real IPs, use it.
>
> **`LoadBalancer` type only works in real clouds.** For local clusters (k3d, kind, minikube), use `NodePort` or rely on the built-in ingress instead.
>
> **`ERR_EMPTY_RESPONSE` vs `Connection Refused`:** `ERR_EMPTY_RESPONSE` means TCP connected but got no HTTP data. `Connection Refused` means TCP couldn't connect at all. The former usually means a proxy/ingress accepted the connection but has no backend to forward to.

---

## Bug 2 — `GET / 404 (Not Found)` after switching to Traefik

### Symptom

After fixing Bug 1, the browser now shows a proper HTTP 404 page instead of `ERR_EMPTY_RESPONSE`. Progress — Traefik is now receiving requests. But nothing is being routed to the right service.

### What Was Happening

The Ingress resources in `k8s/base/web-frontend-deployment.yaml` were defined with:

```yaml
spec:
  ingressClassName: nginx   # <-- tells Kubernetes: "only nginx should handle this"
```

An **`ingressClassName`** is how a cluster with multiple ingress controllers knows which controller should process each Ingress rule. It works like a label — the controller only picks up Ingress objects that have its name.

Since we removed ingress-nginx, only Traefik is running. Traefik looks for `ingressClassName: traefik`. It sees `ingressClassName: nginx` and completely ignores those Ingress rules. With no matching rules, Traefik returns its default response: **404**.

You can verify which classes are available with:
```bash
kubectl get ingressclass

NAME      CONTROLLER
nginx     k8s.io/ingress-nginx     # <-- no longer installed
traefik   traefik.io/ingress-controller  # <-- this is what's running
```

### Fix

We cannot just change `ingressClassName: nginx` to `traefik` in the base manifests, because the **production server uses ingress-nginx** (it doesn't have Traefik). We need different values per environment.

This is solved with **Kustomize overlays** — a way to keep one base config and apply environment-specific patches on top.

**File structure created:**
```
k8s/
  base/                          # shared by all environments
    web-frontend-deployment.yaml # ingressClassName: nginx  (default/base value)
    ...
  overlays/
    local/                       # applied when deploying to k3d
      kustomization.yaml
      ingress-class-patch.yaml   # overrides ingressClassName → traefik
    production/                  # applied when deploying to production server
      kustomization.yaml         # keeps ingressClassName: nginx (no patch needed)
```

`k8s/overlays/local/ingress-class-patch.yaml`:
```yaml
- op: replace
  path: /spec/ingressClassName
  value: traefik
```

`k8s/overlays/local/kustomization.yaml` — applies the patch to both ingress objects:
```yaml
patches:
  - path: ingress-class-patch.yaml
    target:
      kind: Ingress
      name: chess-ingress
  - path: ingress-class-patch.yaml
    target:
      kind: Ingress
      name: kafka-ui-ingress
```

Deploy locally with:
```bash
kubectl apply -k k8s/overlays/local    # uses traefik
```

Deploy to production with:
```bash
kubectl apply -k k8s/overlays/production  # uses nginx
```

### Lesson

> **Ingress controllers are selective.** Each controller only processes Ingress objects that match its `ingressClassName`. If the class doesn't match, the controller silently ignores the rules → 404.
>
> **Kustomize overlays** are the clean way to manage config that differs between environments (local vs production). The base holds shared/neutral config; overlays patch only what's different. This avoids maintaining two separate copies of every file.

---

## Bug 3 — `http://chess.local/kafka-ui` returns `404`

### Symptom

After Bugs 1 and 2 were fixed, three services worked fine:
- `http://chess.local` → ✅ 200
- `http://chess.local/v1/chess/info` → ✅ 200
- `http://chess.local/auth` → ✅ 303 (Keycloak redirect)
- `http://chess.local/kafka-ui` → ❌ 404

### What Was Happening

The Ingress rule says: "forward all requests starting with `/kafka-ui` to the `kafka-ui` service."

Traefik does exactly that — it forwards `GET /kafka-ui` to the Kafka UI pod. But the Kafka UI application itself (a Spring Boot app) is running with its web server bound to the root path `/`. It has no handler registered for `/kafka-ui`, so it returns 404.

Think of it like this:

```
Browser: GET http://chess.local/kafka-ui
    ↓
Traefik: "I have a rule for /kafka-ui → send to kafka-ui pod"
    ↓
Kafka UI pod receives: GET /kafka-ui
    ↓
Spring Boot looks for a handler at path "/kafka-ui" → NOT FOUND → 404
```

The app expected requests at `/`, but was receiving them at `/kafka-ui`. The ingress and the app were out of sync.

### Fix

Tell the Kafka UI Spring Boot app to mount itself at `/kafka-ui` using the `SERVER_SERVLET_CONTEXT_PATH` environment variable.

**Before** (`k8s/base/kafka-ui-deployment.yaml` ConfigMap):
```yaml
data:
  KAFKA_CLUSTERS_0_NAME: chess-cluster
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
  KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
  # no context path → app serves at /
```

**After:**
```yaml
data:
  KAFKA_CLUSTERS_0_NAME: chess-cluster
  KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
  KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
  SERVER_SERVLET_CONTEXT_PATH: /kafka-ui   # app now serves at /kafka-ui
```

And in the Deployment container env:
```yaml
- name: SERVER_SERVLET_CONTEXT_PATH
  valueFrom:
    configMapKeyRef:
      name: kafka-ui-config
      key: SERVER_SERVLET_CONTEXT_PATH
```

Now the flow works:
```
Browser: GET http://chess.local/kafka-ui
    ↓
Traefik: forwards to kafka-ui pod at /kafka-ui
    ↓
Spring Boot: has handler at /kafka-ui → 200 ✅
```

### Lesson

> **The ingress path and the app's internal path must match.** When you expose an app at a subpath (e.g. `/kafka-ui`) via an ingress, the app itself must be configured to serve from that same subpath.
>
> Common ways to set the context path per framework:
>
> | Framework | Setting |
> |---|---|
> | Spring Boot | `SERVER_SERVLET_CONTEXT_PATH=/kafka-ui` |
> | Grafana | `GF_SERVER_ROOT_URL=http://host/grafana` |
> | Prometheus | `--web.external-url=/prometheus` |
> | React (CRA) | `PUBLIC_URL=/app` in build |
>
> The alternative is to use an ingress **rewrite annotation** to strip the prefix before forwarding, but that requires the app to handle redirects correctly — configuring the app is simpler.

---

## Bug 4 — Kafka `CrashLoopBackOff` (exit code 1, silent crash)

### Symptom

```bash
kubectl get pods -n chess

NAME                   READY   STATUS             RESTARTS
kafka-59d586bfdd-xxx   0/1     CrashLoopBackOff   7
```

Kafka is restarting in a loop. The logs show almost nothing:

```
===> User
uid=1000(appuser) gid=1000(appuser) groups=1000(appuser)
===> Configuring ...
Running in Zookeeper mode...
port is deprecated. Please use KAFKA_ADVERTISED_LISTENERS instead.
[process exits with code 1]
```

No error message, no stack trace — just a warning about `port is deprecated` and then it dies.

### What Was Happening

This is a subtle interaction between Kubernetes and the Confluent Kafka Docker image.

**Step 1 — How Kubernetes service discovery works:**

When a pod starts, Kubernetes automatically injects environment variables for every `Service` in the same namespace. For example, because a `Service` named `kafka` exists, Kubernetes injects these vars into *every other pod*:

```bash
# Kubernetes automatically adds these to ALL pods in the "chess" namespace:
KAFKA_PORT=tcp://10.43.174.244:29092
KAFKA_SERVICE_HOST=10.43.174.244
KAFKA_SERVICE_PORT=29092
KAFKA_PORT_29092_TCP=tcp://10.43.174.244:29092
KAFKA_PORT_29092_TCP_PROTO=tcp
KAFKA_PORT_29092_TCP_PORT=29092
KAFKA_PORT_29092_TCP_ADDR=10.43.174.244
```

This is called **service links** — a legacy feature from before Kubernetes had DNS-based discovery.

**Step 2 — How the Confluent Kafka entrypoint works:**

The `confluentinc/cp-kafka` Docker image has an entrypoint script that converts environment variables into Kafka broker configuration. The rule is simple: any env var starting with `KAFKA_` gets converted to a broker property.

```
KAFKA_BROKER_ID=1           → broker.id=1
KAFKA_ADVERTISED_LISTENERS  → advertised.listeners=...
KAFKA_PORT                  → port=tcp://10.43.174.244:29092   ← PROBLEM!
```

`port` is an old, deprecated Kafka broker property. When the entrypoint sets both `port` (from `KAFKA_PORT`) AND `listeners` (from `KAFKA_LISTENERS`), Kafka's startup validation detects a conflict between the deprecated and modern listener config → **exits with code 1**.

The entrypoint even warns you: `port is deprecated. Please use KAFKA_ADVERTISED_LISTENERS instead.` But the warning message is misleading — the problem isn't your config, it's the injected `KAFKA_PORT` env var from Kubernetes.

**Visualizing the conflict:**
```
Kubernetes injects:     KAFKA_PORT=tcp://10.43.x.x:29092
  ↓ entrypoint converts to:
Kafka config:           port=tcp://10.43.x.x:29092   ← deprecated

Your config:            KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:29092
  ↓ entrypoint converts to:
Kafka config:           listeners=PLAINTEXT://0.0.0.0:29092  ← modern

Kafka startup:          "port AND listeners are both set → ERROR" → exit 1
```

### How We Found It

Since `kubectl logs` only showed 4 lines, we had to check the environment variables inside the Zookeeper pod (which is in the same namespace) to see what Kubernetes was injecting:

```bash
kubectl exec -n chess deployment/zookeeper -- env | grep KAFKA
# Output showed KAFKA_PORT, KAFKA_SERVICE_PORT, etc. being injected
```

That revealed the `KAFKA_PORT` injection, which explained the deprecated `port` warning.

### Fix

Add `enableServiceLinks: false` to the Kafka pod spec. This tells Kubernetes **not** to inject service discovery env vars into this pod.

**Before** (`k8s/base/kafka-deployment.yaml`):
```yaml
spec:
  template:
    spec:
      containers:
      - name: kafka
        image: confluentinc/cp-kafka:7.5.0
        env:
        - name: KAFKA_BROKER_ID
          value: "1"
        # ... etc
```

**After:**
```yaml
spec:
  template:
    spec:
      enableServiceLinks: false    # ← add this one line
      containers:
      - name: kafka
        image: confluentinc/cp-kafka:7.5.0
        env:
        - name: KAFKA_BROKER_ID
          value: "1"
        # ... etc
```

With `enableServiceLinks: false`, Kubernetes no longer injects `KAFKA_PORT` into the pod. The entrypoint only sees your explicitly defined env vars, and Kafka starts successfully.

### Lesson

> **Kubernetes injects service env vars into every pod by default.** For most apps this is harmless. But for apps that consume all env vars matching a prefix (like Confluent's `KAFKA_*` pattern), this creates invisible, hard-to-debug conflicts.
>
> **Always use `enableServiceLinks: false`** for Confluent Kafka, Confluent Schema Registry, or any app that maps env vars to config by prefix.
>
> **Use DNS instead of env vars for service discovery.** The modern Kubernetes way is DNS: `kafka.chess.svc.cluster.local:29092` — it always works, doesn't depend on injection order, and doesn't pollute the env.
>
> **Debugging tip:** When a pod crashes silently with no useful log, check `kubectl exec` on a healthy pod in the same namespace and run `env` to see what Kubernetes is injecting.

---

## Bug 5 — Hardcoded Production IPs in Local Deployment

### Symptom

Not a crash, but a subtle misconfiguration. The Keycloak service was rejecting login redirects, and the frontend was calling the wrong API server.

### What Was Happening

Several Kubernetes ConfigMaps had the production server's IP address (`141.37.123.124`) hardcoded directly:

```yaml
# k8s/keycloak-deployment.yaml (BEFORE)
data:
  KC_HOSTNAME: "141.37.123.124"   # ← production IP in local config!
```

```yaml
# k8s/web-frontend-deployment.yaml (BEFORE)
data:
  REACT_APP_API_URL: http://141.37.123.124/v1   # ← calls production server!
```

When running locally in k3d, Keycloak was telling browsers to redirect to `141.37.123.124` for authentication (which is the live server, not localhost). The frontend was sending API requests to the production server instead of the local cluster.

### Fix

Restructured all Kubernetes manifests using **Kustomize overlays** to separate environment-specific values from shared config:

```
k8s/
  base/                              # neutral defaults (no IPs)
    keycloak-deployment.yaml         # KC_HOSTNAME: "localhost"
    web-frontend-deployment.yaml     # REACT_APP_API_URL: http://localhost/v1
    ...
  overlays/
    local/                           # applied for k3d
      keycloak-config-patch.yaml     # KC_HOSTNAME: "chess.local"
      web-frontend-config-patch.yaml # REACT_APP_API_URL: http://chess.local/v1
      ingress-class-patch.yaml       # ingressClassName: traefik
    production/                      # applied for 141.37.123.124
      keycloak-config-patch.yaml     # KC_HOSTNAME: "141.37.123.124"
      web-frontend-config-patch.yaml # REACT_APP_API_URL: http://141.37.123.124/v1
```

Each overlay patch file only contains the keys that differ — everything else is inherited from base.

**Example patch** (`k8s/overlays/local/keycloak-config-patch.yaml`):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-config
  namespace: chess
data:
  KC_HOSTNAME: "chess.local"    # only override what's different
  KC_HOSTNAME_PORT: "80"
```

### Lesson

> **Never hardcode environment-specific values (IPs, hostnames, URLs) in base Kubernetes manifests.** When someone clones the repo and runs the local setup, they should not be accidentally calling production services.
>
> **Kustomize is the standard tool** for this in Kubernetes. An alternative is Helm with `values.yaml` files per environment. Both achieve the same goal: one source of truth with environment-specific overrides.

---

## Summary of All Changes

| File | What Changed | Why |
|---|---|---|
| `scripts/setup-k3d.ps1` | Removed ingress-nginx install; port changed to `80:80@loadbalancer` | Bug 1: k3d has Traefik built-in |
| `scripts/setup-k3d.sh` | Same as above | Bug 1 |
| `k8s/base/kafka-deployment.yaml` | Added `enableServiceLinks: false` | Bug 4: prevents `KAFKA_PORT` injection |
| `k8s/base/kafka-ui-deployment.yaml` | Added `SERVER_SERVLET_CONTEXT_PATH: /kafka-ui` | Bug 3: app must serve from its ingress subpath |
| `k8s/base/keycloak-deployment.yaml` | `KC_HOSTNAME: localhost` (neutral default) | Bug 5: no hardcoded IPs in base |
| `k8s/base/web-frontend-deployment.yaml` | `REACT_APP_API_URL: http://localhost/v1` (neutral default) | Bug 5 |
| `k8s/overlays/local/ingress-class-patch.yaml` | New file — patches `ingressClassName` to `traefik` | Bug 2 |
| `k8s/overlays/local/kustomization.yaml` | Registers all local patches | Bugs 2, 5 |
| `k8s/overlays/local/keycloak-config-patch.yaml` | `KC_HOSTNAME: chess.local` | Bug 5 |
| `k8s/overlays/local/web-frontend-config-patch.yaml` | `REACT_APP_API_URL: http://chess.local/v1` | Bug 5 |
| `k8s/overlays/production/keycloak-config-patch.yaml` | `KC_HOSTNAME: 141.37.123.124` | Bug 5 |
| `k8s/overlays/production/web-frontend-config-patch.yaml` | `REACT_APP_API_URL: http://141.37.123.124/v1` | Bug 5 |

---

## Working Local URLs (after all fixes)

All services route through Traefik on port 80:

| Service | URL | HTTP Status |
|---|---|---|
| Web Frontend | http://chess.local | 200 |
| REST API | http://chess.local/v1 | 200 |
| Keycloak | http://chess.local/auth | 303 (redirect to login) |
| Kafka UI | http://chess.local/kafka-ui | 200 |

---

## Quick Reference: Useful Debugging Commands

```bash
# Check what ingress controllers are available
kubectl get ingressclass

# Check what is running in kube-system (Traefik, CoreDNS, etc.)
kubectl get pods -n kube-system
kubectl get svc -n kube-system

# Check if an ingress got picked up and has an IP
kubectl get ingress -n chess

# See what environment variables Kubernetes is injecting into a pod
kubectl exec -n chess deployment/zookeeper -- env | sort

# See why a pod is crashing
kubectl describe pod -n chess <pod-name>
kubectl logs -n chess <pod-name> --previous

# Check all pod statuses at once
kubectl get pods -n chess

# Test an endpoint from your machine
curl -v http://chess.local/v1/chess/info
```
