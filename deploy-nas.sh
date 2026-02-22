#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Synology NAS Docker Deploy Script
# Usage: ./deploy-nas.sh [options]
# ============================================================

# --- Configuration (override with environment variables) ---
NAS_HOST="${NAS_HOST:-nas}"
NAS_USER="${NAS_USER:-pdg1025}"
NAS_PORT="${NAS_PORT:-2022}"
NAS_DEPLOY_DIR="${NAS_DEPLOY_DIR:-/volume1/docker/sotti-kindergarten}"
IMAGE_NAME="${IMAGE_NAME:-sotti-kindergarten}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
PLATFORM="${PLATFORM:-linux/amd64}"
HEALTHCHECK_PORT="${HEALTHCHECK_PORT:-1025}"
HEALTHCHECK_TIMEOUT="${HEALTHCHECK_TIMEOUT:-60}"

# --- Flags ---
UPDATE_COMPOSE=false
UPDATE_ENV=false
SKIP_BUILD=false

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

usage() {
    cat <<'EOF'
Synology NAS Docker Deploy Script

Usage: ./deploy-nas.sh [options]

Options:
  --update-compose   Re-upload docker-compose.yml to NAS
  --update-env       Re-upload .env file to NAS
  --skip-build       Skip Docker image build (use existing image)
  --help             Show this help message

Environment Variables:
  NAS_HOST           NAS hostname or IP (default: nas)
  NAS_USER           SSH user (default: pdg1025)
  NAS_PORT           SSH port (default: 2022)
  NAS_DEPLOY_DIR     Deploy directory on NAS (default: /volume1/docker/sotti-kindergarten)
  IMAGE_NAME         Docker image name (default: sotti-kindergarten)
  IMAGE_TAG          Docker image tag (default: latest)
  PLATFORM           Target platform (default: linux/amd64)
  HEALTHCHECK_PORT   Port for health check (default: 1025)
  HEALTHCHECK_TIMEOUT  Health check timeout in seconds (default: 60)

Examples:
  ./deploy-nas.sh                          # Full deploy
  ./deploy-nas.sh --update-compose         # Deploy + update compose file
  ./deploy-nas.sh --skip-build             # Deploy with existing image
  IMAGE_TAG=v1.0.0 ./deploy-nas.sh        # Deploy specific version
  NAS_HOST=192.168.1.100 ./deploy-nas.sh  # Override NAS host
EOF
    exit 0
}

# --- Parse arguments ---
for arg in "$@"; do
    case $arg in
        --update-compose) UPDATE_COMPOSE=true ;;
        --update-env)     UPDATE_ENV=true ;;
        --skip-build)     SKIP_BUILD=true ;;
        --help)           usage ;;
        *) log_error "Unknown option: $arg"; usage ;;
    esac
done

FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
SSH_CMD="ssh -p ${NAS_PORT} ${NAS_USER}@${NAS_HOST}"
# Synology Docker path (not in default PATH for non-login shells)
NAS_DOCKER_PATH="/volume1/@appstore/ContainerManager/usr/bin"
REMOTE_CMD_PREFIX="export PATH=\$PATH:${NAS_DOCKER_PATH} &&"

# ============================================================
# Step 1: SSH Connection Check
# ============================================================
log_info "Checking SSH connection to ${NAS_USER}@${NAS_HOST}:${NAS_PORT}..."

if ! ${SSH_CMD} "echo ok" &>/dev/null; then
    log_error "SSH connection failed. Check your SSH key setup."
    echo "  ssh-copy-id -p ${NAS_PORT} ${NAS_USER}@${NAS_HOST}"
    exit 1
fi
log_ok "SSH connection successful"

# ============================================================
# Step 2: Ensure deploy directory exists on NAS
# ============================================================
log_info "Ensuring deploy directory exists: ${NAS_DEPLOY_DIR}"
${SSH_CMD} "mkdir -p ${NAS_DEPLOY_DIR}"
log_ok "Deploy directory ready"

# ============================================================
# Step 3: Upload docker-compose.yml (first time or --update-compose)
# ============================================================
COMPOSE_EXISTS=$(${SSH_CMD} "test -f ${NAS_DEPLOY_DIR}/docker-compose.yml && echo yes || echo no")

if [[ "${COMPOSE_EXISTS}" == "no" ]] || [[ "${UPDATE_COMPOSE}" == "true" ]]; then
    log_info "Uploading docker-compose.yml to NAS..."
    cat docker-compose.nas.yml | ${SSH_CMD} "cat > ${NAS_DEPLOY_DIR}/docker-compose.yml"
    log_ok "docker-compose.yml uploaded"
else
    log_info "docker-compose.yml already exists on NAS (use --update-compose to update)"
fi

# ============================================================
# Step 4: Upload .env file (first time or --update-env)
# ============================================================
if [[ ! -f .env ]]; then
    log_error "Local .env file not found. Create it first (see .env.example)."
    exit 1
fi

ENV_EXISTS=$(${SSH_CMD} "test -f ${NAS_DEPLOY_DIR}/.env && echo yes || echo no")

if [[ "${ENV_EXISTS}" == "no" ]] || [[ "${UPDATE_ENV}" == "true" ]]; then
    log_info "Uploading .env to NAS..."
    cat .env | ${SSH_CMD} "cat > ${NAS_DEPLOY_DIR}/.env"
    log_ok ".env uploaded"
else
    log_info ".env already exists on NAS (use --update-env to update)"
fi

# ============================================================
# Step 5: Build Docker image
# ============================================================
if [[ "${SKIP_BUILD}" == "true" ]]; then
    log_info "Skipping build (--skip-build)"
else
    log_info "Building Docker image: ${FULL_IMAGE} (platform: ${PLATFORM})"
    log_info "This may take a few minutes..."

    docker buildx build \
        --platform "${PLATFORM}" \
        --tag "${FULL_IMAGE}" \
        --load \
        .

    log_ok "Docker image built: ${FULL_IMAGE}"
fi

# ============================================================
# Step 6: Transfer image to NAS via SSH pipe
# ============================================================
log_info "Transferring image to NAS (docker save | ssh docker load)..."
log_info "This may take a while depending on image size and network speed..."

docker save "${FULL_IMAGE}" | ${SSH_CMD} "${REMOTE_CMD_PREFIX} docker load"

log_ok "Image transferred and loaded on NAS"

# ============================================================
# Step 7: Deploy with docker compose
# ============================================================
log_info "Deploying container on NAS..."

${SSH_CMD} "${REMOTE_CMD_PREFIX} cd ${NAS_DEPLOY_DIR} && IMAGE_TAG=${IMAGE_TAG} docker compose up -d --force-recreate"

log_ok "Container deployed"

# ============================================================
# Step 8: Health check
# ============================================================
log_info "Waiting for application to start (timeout: ${HEALTHCHECK_TIMEOUT}s)..."

ELAPSED=0
INTERVAL=5

while [[ ${ELAPSED} -lt ${HEALTHCHECK_TIMEOUT} ]]; do
    HEALTH=$(${SSH_CMD} "curl -sf http://localhost:${HEALTHCHECK_PORT}/actuator/health 2>/dev/null" || echo fail)

    if echo "${HEALTH}" | grep -q '"status":"UP"'; then
        echo ""
        log_ok "Application is healthy!"
        echo ""
        log_info "=== Deploy Complete ==="
        log_info "Image: ${FULL_IMAGE}"
        log_info "NAS:   ${NAS_USER}@${NAS_HOST}:${NAS_DEPLOY_DIR}"
        log_info "URL:   http://${NAS_HOST}:${HEALTHCHECK_PORT}/actuator/health"
        exit 0
    fi

    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
    printf "."
done

echo ""
log_error "Health check timed out after ${HEALTHCHECK_TIMEOUT}s"
log_warn "Check logs: ssh ${NAS_USER}@${NAS_HOST} 'cd ${NAS_DEPLOY_DIR} && docker compose logs -f'"
exit 1
