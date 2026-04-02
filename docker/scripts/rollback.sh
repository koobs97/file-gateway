#!/usr/bin/env bash
# =====================================================================
# 롤백 스크립트
#
# 롤백 대상 판단 (컨테이너 실행 상태 기반):
#   - upstream.conf 의 현재 활성 슬롯의 반대편 슬롯으로 전환
#   - 반대편 슬롯이 존재하지 않으면 docker compose로 기동 후 전환
#
# 인프라 충돌 처리: deploy.sh 와 동일 (로컬 개발용 컨테이너 재사용)
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
UPSTREAM_CONF="$DOCKER_DIR/nginx/conf.d/upstream.conf"

HEALTH_HOST="${HEALTH_HOST:-host.docker.internal}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[ROLLBACK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}    $*"; }
error() { echo -e "${RED}[ERROR]${NC}   $*" >&2; }

if [[ ! -f "$ENV_FILE" ]]; then
    error ".env.prod 파일이 없습니다: $ENV_FILE"
    exit 1
fi

set -a; source "$ENV_FILE"; set +a

# ── 인프라 자동 기동 (deploy.sh와 동일 로직) ──────────────────────────
info "인프라 상태 확인 중..."

if docker network inspect "file-gateway-net" > /dev/null 2>&1; then
    info "  네트워크 이미 존재: file-gateway-net"
else
    docker network create file-gateway-net
    info "  네트워크 생성: file-gateway-net"
fi

_ensure_container() {
    local NAME="$1"
    local COMPOSE_SVC="$2"

    if docker inspect "$NAME" > /dev/null 2>&1; then
        local STATUS
        STATUS=$(docker inspect --format='{{.State.Status}}' "$NAME" 2>/dev/null || echo "")
        if [[ "$STATUS" != "running" ]]; then
            docker start "$NAME" 2>/dev/null || true
        fi
        docker network connect file-gateway-net "$NAME" 2>/dev/null || true
        info "  $NAME: 기존 컨테이너 재사용 (상태: $STATUS)"
    else
        docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d "$COMPOSE_SVC"
        info "  $NAME: 신규 생성"
    fi
}

_ensure_container "file-gateway-postgres" "postgres"
_ensure_container "file-gateway-nginx" "nginx"
sleep 2

# ── 현재 활성 슬롯 판단 (upstream.conf 기반) ───────────────────────────
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";  FALLBACK="green"; FALLBACK_PORT=8082
else
    ACTIVE="green"; FALLBACK="blue";  FALLBACK_PORT=8081
fi
info "현재 활성: $ACTIVE  →  롤백 대상: $FALLBACK (포트 $FALLBACK_PORT)"

# ── 폴백 슬롯 기동 ─────────────────────────────────────────────────────
FALLBACK_STATUS=$(docker inspect --format='{{.State.Status}}' "file-gateway-$FALLBACK" 2>/dev/null || echo "")
if [[ "$FALLBACK_STATUS" == "running" ]]; then
    warn "폴백 슬롯 [$FALLBACK] 이 이미 실행 중 → --force-recreate 없이 재사용"
else
    warn "폴백 슬롯 [$FALLBACK] 기동 중..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" \
        up -d --no-deps "file-gateway-$FALLBACK"
fi

# ── 헬스체크 대기 ─────────────────────────────────────────────────────
info "헬스체크 대기 중 → http://${HEALTH_HOST}:${FALLBACK_PORT}/api/health"
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

# ── Nginx upstream 전환 ────────────────────────────────────────────────
cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (rollback.sh에 의해 롤백됨)
# 현재 활성: $FALLBACK
upstream backend {
    server file-gateway-${FALLBACK}:8080;
}
EOF

if docker inspect "file-gateway-nginx" > /dev/null 2>&1; then
    NGINX_STATUS=$(docker inspect --format='{{.State.Status}}' "file-gateway-nginx" 2>/dev/null || echo "")
    if [[ "$NGINX_STATUS" == "running" ]]; then
        docker exec file-gateway-nginx nginx -s reload
        info "Nginx upstream 전환 완료: $ACTIVE → $FALLBACK"
    fi
fi

# ── 장애 슬롯 중지 ────────────────────────────────────────────────────
ACTIVE_STATUS=$(docker inspect --format='{{.State.Status}}' "file-gateway-$ACTIVE" 2>/dev/null || echo "")
if [[ "$ACTIVE_STATUS" == "running" ]]; then
    info "장애 슬롯 [$ACTIVE] 중지 중..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$ACTIVE" || true
fi

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "롤백 완료! 활성 슬롯: $FALLBACK"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
