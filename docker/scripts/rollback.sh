#!/usr/bin/env bash
# =====================================================================
# 롤백 스크립트
#
# 동작 원리:
#  현재 활성 슬롯의 반대 슬롯을 기동하고 nginx upstream을 즉시 전환
#  (컨테이너가 없는 첫 배포 상황도 처리)
#
# 사용법:
#  ./docker/scripts/rollback.sh
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

# ── 인프라 자동 기동 (네트워크가 없으면 인프라부터 시작) ─────────────
if ! docker network ls --format '{{.Name}}' | grep -q "^file-gateway-net$"; then
    warn "file-gateway-net 네트워크가 없습니다. 인프라를 시작합니다..."
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d
    info "인프라 기동 대기 중 (10초)..."
    sleep 10
fi

# ── 현재 활성 슬롯 판단 ───────────────────────────────────────────────
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";   FALLBACK="green"
    FALLBACK_PORT=8082
else
    ACTIVE="green";  FALLBACK="blue"
    FALLBACK_PORT=8081
fi

info "현재 활성: $ACTIVE  →  롤백 대상: $FALLBACK"

# ── 폴백 슬롯 기동 ────────────────────────────────────────────────────
# docker compose up -d 는 컨테이너가 없어도(최초), 중지 상태여도 모두 처리함
warn "폴백 슬롯 $FALLBACK 기동 중..."
docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" \
    up -d --no-deps "file-gateway-$FALLBACK"

# ── 헬스체크 대기 ────────────────────────────────────────────────────
for i in $(seq 1 20); do
    HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
        "http://localhost:${FALLBACK_PORT}/api/health" 2>/dev/null || echo "000")
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

# ── Nginx upstream 전환 ───────────────────────────────────────────────
# nginx가 없으면 reload 건너뜀 (인프라 방금 기동한 경우)
if docker ps --format '{{.Names}}' | grep -q "file-gateway-nginx"; then
    info "Nginx upstream 전환: $ACTIVE → $FALLBACK"
    cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (rollback.sh에 의해 롤백됨)
# 현재 활성: $FALLBACK
upstream backend {
    server file-gateway-${FALLBACK}:8080;
}
EOF
    docker exec file-gateway-nginx nginx -s reload
else
    warn "Nginx 컨테이너가 없습니다. upstream.conf 파일만 업데이트합니다."
    cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (rollback.sh에 의해 롤백됨)
# 현재 활성: $FALLBACK
upstream backend {
    server file-gateway-${FALLBACK}:8080;
}
EOF
fi

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "롤백 완료!"
info "  활성 슬롯: $FALLBACK  (이전: $ACTIVE)"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
