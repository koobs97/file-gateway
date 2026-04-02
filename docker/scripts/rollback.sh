#!/usr/bin/env bash
# =====================================================================
# 롤백 스크립트
#
# 롤백 대상 판단: nginx upstream.conf(컨테이너 내부) 기준으로 현재 활성 슬롯 반대편으로 전환
# 인프라 충돌 처리: deploy.sh와 동일 (로컬 개발용 컨테이너 재사용)
# upstream.conf 업데이트: docker cp 방식 (Jenkins named volume 경로 문제 회피)
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/upstream.conf"

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

# ── 헬퍼 ──────────────────────────────────────────────────────────────
_is_running() {
    local STATUS
    STATUS=$(docker inspect --format='{{.State.Status}}' "$1" 2>/dev/null || echo "")
    [[ "$STATUS" == "running" ]]
}

_update_upstream() {
    local SLOT="$1"
    local TMPFILE
    TMPFILE=$(mktemp)
    cat > "$TMPFILE" << EOF
# Blue-Green upstream 변수 (rollback.sh에 의해 롤백됨)
# 현재 활성: $SLOT
map "" \$backend_host {
    default "file-gateway-${SLOT}";
}
map "" \$frontend_host {
    default "file-gateway-frontend";
}
EOF
    docker cp "$TMPFILE" "file-gateway-nginx:${NGINX_UPSTREAM_CONF}"
    rm -f "$TMPFILE"
    docker exec file-gateway-nginx nginx -s reload
    info "Nginx upstream 전환 완료 → [$SLOT]"
}

# ── 인프라 자동 기동 ───────────────────────────────────────────────────
info "인프라 상태 확인 중..."

if docker network inspect "file-gateway-net" > /dev/null 2>&1; then
    info "  네트워크 이미 존재: file-gateway-net"
else
    docker network create file-gateway-net
    info "  네트워크 생성: file-gateway-net"
fi

# postgres 재사용
if docker inspect "file-gateway-postgres" > /dev/null 2>&1; then
    _is_running "file-gateway-postgres" || docker start "file-gateway-postgres" 2>/dev/null || true
    docker network connect file-gateway-net "file-gateway-postgres" 2>/dev/null || true
    info "  postgres: 기존 컨테이너 재사용"
else
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d --no-deps postgres
    info "  postgres: 신규 생성"
fi

# nginx 재사용 (없으면 신규 생성)
if docker inspect "file-gateway-nginx" > /dev/null 2>&1; then
    _is_running "file-gateway-nginx" || docker start "file-gateway-nginx" 2>/dev/null || true
    docker network connect file-gateway-net "file-gateway-nginx" 2>/dev/null || true
    # 기존 컨테이너도 conf 파일 항상 최신화
    # (볼륨 마운트 방식의 이전 버전 conf가 남아있을 수 있음, reload는 _update_upstream에서 처리)
    docker cp "$DOCKER_DIR/nginx/nginx.conf" file-gateway-nginx:/etc/nginx/nginx.conf 2>/dev/null || true
    docker cp "$DOCKER_DIR/nginx/conf.d/app.conf" file-gateway-nginx:/etc/nginx/conf.d/app.conf 2>/dev/null || true
    info "  nginx: 기존 컨테이너 재사용 (conf 갱신)"
else
    info "  nginx: 신규 생성 (docker run + docker cp)"
    docker run -d \
        --name file-gateway-nginx \
        --network file-gateway-net \
        -p 80:80 \
        --restart unless-stopped \
        nginx:alpine
    sleep 2
    docker cp "$DOCKER_DIR/nginx/nginx.conf" file-gateway-nginx:/etc/nginx/nginx.conf
    docker cp "$DOCKER_DIR/nginx/conf.d/app.conf" file-gateway-nginx:/etc/nginx/conf.d/app.conf
    docker cp "$DOCKER_DIR/nginx/conf.d/upstream.conf" file-gateway-nginx:/etc/nginx/conf.d/upstream.conf
    docker exec file-gateway-nginx nginx -s reload
    info "  nginx: 신규 생성 완료"
fi

sleep 2

# ── 현재 활성 슬롯 판단 (nginx 컨테이너 내부 upstream.conf 기준) ──────
if docker exec file-gateway-nginx \
    cat "$NGINX_UPSTREAM_CONF" 2>/dev/null | grep -q '"file-gateway-blue"'; then
    ACTIVE="blue";  FALLBACK="green"; FALLBACK_PORT=8082
else
    ACTIVE="green"; FALLBACK="blue";  FALLBACK_PORT=8081
fi
info "현재 활성: $ACTIVE  →  롤백 대상: $FALLBACK (포트 $FALLBACK_PORT)"

# ── 폴백 슬롯 기동 ─────────────────────────────────────────────────────
if _is_running "file-gateway-$FALLBACK"; then
    warn "폴백 슬롯 [$FALLBACK] 이 이미 실행 중 → 재사용"
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

# ── Nginx upstream 전환 (docker cp 방식) ────────────────────────────────
_update_upstream "$FALLBACK"

# ── 장애 슬롯 중지 ────────────────────────────────────────────────────
if _is_running "file-gateway-$ACTIVE"; then
    info "장애 슬롯 [$ACTIVE] 중지 중..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$ACTIVE" || true
fi

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "롤백 완료! 활성 슬롯: $FALLBACK"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
