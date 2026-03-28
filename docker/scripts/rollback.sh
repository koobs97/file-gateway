#!/usr/bin/env bash
# =====================================================================
# 롤백 스크립트
#
# 동작 원리:
#  현재 활성 슬롯의 반대 슬롯이 중지 상태라면 즉시 활성화하고 전환
#  (이전 배포가 성공한 슬롯으로 트래픽을 즉시 복구)
#
# 사용법:
#  ./docker/scripts/rollback.sh
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
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

# 현재 활성 판단
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";   FALLBACK="green"
    FALLBACK_PORT=8082
else
    ACTIVE="green";  FALLBACK="blue"
    FALLBACK_PORT=8081
fi

info "현재 활성: $ACTIVE  →  롤백 대상: $FALLBACK"

# 폴백 슬롯 상태 확인
FALLBACK_RUNNING=$(docker ps --format '{{.Names}}' | grep -c "file-gateway-$FALLBACK" || true)

if [[ "$FALLBACK_RUNNING" -eq 0 ]]; then
    warn "폴백 슬롯 $FALLBACK 이 중지 상태입니다. 재시작합니다..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" start "file-gateway-$FALLBACK" \
        || docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" up -d --no-deps "file-gateway-$FALLBACK"

    # 헬스체크
    for i in $(seq 1 15); do
        HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
            "http://localhost:${FALLBACK_PORT}/api/health" 2>/dev/null || echo "000")
        if [[ "$HTTP_STATUS" == "200" ]]; then
            info "폴백 슬롯 준비 완료 (${i}/15)"
            break
        fi
        if [[ "$i" -eq 15 ]]; then
            error "폴백 슬롯 시작 실패! 수동 확인이 필요합니다."
            exit 1
        fi
        sleep 2
    done
fi

# Nginx 전환
info "Nginx upstream 전환: $ACTIVE → $FALLBACK"
cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (rollback.sh에 의해 롤백됨)
# 현재 활성: $FALLBACK
upstream backend {
    server file-gateway-${FALLBACK}:8080;
}
EOF

docker exec file-gateway-nginx nginx -s reload

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "롤백 완료!"
info "  활성 슬롯: $FALLBACK  (이전: $ACTIVE)"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
