#!/usr/bin/env bash
# =====================================================================
# 롤백 스크립트
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
UPSTREAM_CONF="$DOCKER_DIR/nginx/conf.d/upstream.conf"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[ROLLBACK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}    $*"; }
error() { echo -e "${RED}[ERROR]${NC}   $*" >&2; }

if [[ ! -f "$ENV_FILE" ]]; then
    error ".env.prod 파일이 없습니다: $ENV_FILE"
    exit 1
fi

set -a; source "$ENV_FILE"; set +a

# ── 인프라 자동 기동 (deploy.sh와 동일 로직) ─────────────────────────
info "인프라 상태 확인 중..."

docker network create file-gateway-net 2>/dev/null || true

if docker ps -a --format '{{.Names}}' | grep -q "^file-gateway-postgres$"; then
    docker start file-gateway-postgres 2>/dev/null || true
    docker network connect file-gateway-net file-gateway-postgres 2>/dev/null || true
else
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d postgres
fi

if docker ps -a --format '{{.Names}}' | grep -q "^file-gateway-nginx$"; then
    docker start file-gateway-nginx 2>/dev/null || true
    docker network connect file-gateway-net file-gateway-nginx 2>/dev/null || true
else
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d nginx
    sleep 3
fi

# ── 현재 활성 슬롯 판단 ──────────────────────────────────────────────
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";   FALLBACK="green"
    FALLBACK_PORT=8082
else
    ACTIVE="green";  FALLBACK="blue"
    FALLBACK_PORT=8081
fi

info "현재 활성: $ACTIVE  →  롤백 대상: $FALLBACK"

# ── 폴백 슬롯 기동 ───────────────────────────────────────────────────
warn "폴백 슬롯 $FALLBACK 기동 중..."
docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" \
    up -d --no-deps "file-gateway-$FALLBACK"

# ── 헬스체크 대기 ────────────────────────────────────────────────────
HEALTH_HOST="${HEALTH_HOST:-host.docker.internal}"
for i in $(seq 1 20); do
    HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
        "http://${HEALTH_HOST}:${FALLBACK_PORT}/api/health" 2>/dev/null || echo "000")
    if [[ "$HTTP_STATUS" == "200" ]]; then
        info "폴백 슬롯 준비 완료 (${i}/20)"
        break
    fi
    if [[ "$i" -eq 20 ]]; then
        error "폴백 슬롯 헬스체크 실패! 수동 확인이 필요합니다."
        exit 1
    fi
    sleep 3
done

# ── Nginx upstream 전환 ──────────────────────────────────────────────
cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (rollback.sh에 의해 롤백됨)
# 현재 활성: $FALLBACK
upstream backend {
    server file-gateway-${FALLBACK}:8080;
}
EOF

if docker ps --format '{{.Names}}' | grep -q "file-gateway-nginx"; then
    docker exec file-gateway-nginx nginx -s reload
    info "Nginx upstream 전환 완료: $ACTIVE → $FALLBACK"
fi

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "롤백 완료! 활성 슬롯: $FALLBACK"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
