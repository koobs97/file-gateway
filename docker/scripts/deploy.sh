#!/usr/bin/env bash
# =====================================================================
# Blue-Green 무중단 배포 스크립트
#
# 배포 모드 자동 판단 (컨테이너 실행 상태 기반):
#   1. 초기 배포  : blue/green 모두 없음 → blue 직접 기동
#   2. 무중단 배포: blue만 실행 중       → green 기동 후 트래픽 전환
#                   green만 실행 중      → blue 기동 후 트래픽 전환
#                   둘 다 실행 중        → nginx upstream.conf 기준으로 inactive 교체
#
# 인프라 충돌 처리:
#   - postgres: docker inspect로 존재 여부 확인 후 재사용 (로컬 개발용 포함)
#   - nginx:    없을 때 docker run + docker cp 방식으로 conf 주입
#               (Jenkins named volume 환경에서 볼륨 마운트 경로 문제 회피)
#   - upstream.conf 업데이트: 파일 직접 수정 대신 docker cp 사용
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
# nginx 컨테이너 내부 upstream.conf 경로
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/upstream.conf"

IMAGE_TAG="${1:-latest}"
HEALTH_RETRIES=30
HEALTH_INTERVAL=2
HEALTH_HOST="${HEALTH_HOST:-host.docker.internal}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[DEPLOY]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 환경 변수 확인 ─────────────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
    error ".env.prod 파일이 없습니다: $ENV_FILE"
    exit 1
fi

set -a; source "$ENV_FILE"; set +a
export IMAGE_TAG

info "배포 시작 — 이미지 태그: $IMAGE_TAG"

# ── 헬퍼: 컨테이너 실행 여부 확인 ─────────────────────────────────────
_is_running() {
    local STATUS
    STATUS=$(docker inspect --format='{{.State.Status}}' "$1" 2>/dev/null || echo "")
    [[ "$STATUS" == "running" ]]
}

# ── 헬퍼: upstream.conf를 docker cp로 업데이트 ─────────────────────────
# Jenkins named volume 환경에서는 볼륨 마운트 경로를 Docker 데몬이 볼 수 없으므로
# 파일 직접 수정 대신 docker cp로 컨테이너에 직접 주입한다
_update_upstream() {
    local SLOT="$1"
    local TAG="${2:-}"
    local TMPFILE
    TMPFILE=$(mktemp)
    # \$ 이스케이프: heredoc 안의 nginx 변수명($backend_host 등)이 shell에 의해 치환되지 않도록
    # map 지시어: http 블록 레벨에서 사용 가능 (set은 server/location 전용이라 conf.d glob include 시 에러)
    cat > "$TMPFILE" << EOF
# Blue-Green upstream 변수 (deploy.sh에 의해 자동 갱신됨)
# 현재 활성: $SLOT${TAG:+ (이미지 태그: $TAG)}
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
    info "Nginx reload 완료 → 트래픽이 [$SLOT] 로 전환되었습니다."
}

# ── 헬퍼: nginx 컨테이너 내부 upstream.conf에서 활성 슬롯 읽기 ─────────
_get_active_slot() {
    if docker exec file-gateway-nginx \
        cat "$NGINX_UPSTREAM_CONF" 2>/dev/null | grep -q '"file-gateway-blue"'; then
        echo "blue"
    else
        echo "green"
    fi
}

# ── 인프라 자동 기동 ───────────────────────────────────────────────────
info "인프라 상태 확인 중..."

# 1. 네트워크 생성 (이미 있으면 무시)
if docker network inspect "file-gateway-net" > /dev/null 2>&1; then
    info "  네트워크 이미 존재: file-gateway-net"
else
    docker network create file-gateway-net
    info "  네트워크 생성: file-gateway-net"
fi

# 2. postgres: 로컬 개발 인프라로 이미 떠있어도 이름 충돌 없이 재사용
if docker inspect "file-gateway-postgres" > /dev/null 2>&1; then
    _is_running "file-gateway-postgres" || docker start "file-gateway-postgres" 2>/dev/null || true
    docker network connect file-gateway-net "file-gateway-postgres" 2>/dev/null || true
    info "  postgres: 기존 컨테이너 재사용"
else
    # --no-deps: depends_on으로 인한 다른 컨테이너 자동 기동 방지
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d --no-deps postgres
    info "  postgres: 신규 생성"
fi

# 3. nginx: 없을 때 docker run + docker cp 방식 (볼륨 마운트 경로 문제 회피)
#    Jenkins는 named volume 안에서 실행되므로 Docker 데몬이 워크스페이스 경로를 볼 수 없음
#    → docker run으로 기동 후 conf 파일을 docker cp로 직접 주입
if docker inspect "file-gateway-nginx" > /dev/null 2>&1; then
    _is_running "file-gateway-nginx" || docker start "file-gateway-nginx" 2>/dev/null || true
    docker network connect file-gateway-net "file-gateway-nginx" 2>/dev/null || true
    info "  nginx: 기존 컨테이너 재사용"
else
    info "  nginx: 신규 생성 (docker run + docker cp)"
    docker run -d \
        --name file-gateway-nginx \
        --network file-gateway-net \
        -p 80:80 \
        --restart unless-stopped \
        nginx:alpine
    sleep 2
    # conf 파일 주입 (볼륨 마운트 대신 docker cp 사용)
    docker cp "$DOCKER_DIR/nginx/nginx.conf" file-gateway-nginx:/etc/nginx/nginx.conf
    docker cp "$DOCKER_DIR/nginx/conf.d/app.conf" file-gateway-nginx:/etc/nginx/conf.d/app.conf
    docker cp "$DOCKER_DIR/nginx/conf.d/upstream.conf" file-gateway-nginx:/etc/nginx/conf.d/upstream.conf
    docker exec file-gateway-nginx nginx -s reload
    info "  nginx: 신규 생성 완료"
fi

# ── 앱 컨테이너 실행 상태 확인 ─────────────────────────────────────────
BLUE_RUNNING=false
GREEN_RUNNING=false
_is_running "file-gateway-blue"  && BLUE_RUNNING=true
_is_running "file-gateway-green" && GREEN_RUNNING=true

info "컨테이너 상태 — blue: $BLUE_RUNNING / green: $GREEN_RUNNING"

# ── 배포 모드 결정 ──────────────────────────────────────────────────────
DEPLOY_MODE=""
ACTIVE=""
INACTIVE=""
INACTIVE_PORT=0

if [[ "$BLUE_RUNNING" == "false" && "$GREEN_RUNNING" == "false" ]]; then
    DEPLOY_MODE="initial"
    INACTIVE="blue"
    INACTIVE_PORT=8081
    info "배포 모드: 초기 배포 → blue 슬롯 기동"

elif [[ "$BLUE_RUNNING" == "true" && "$GREEN_RUNNING" == "false" ]]; then
    DEPLOY_MODE="bluegreen"
    ACTIVE="blue"
    INACTIVE="green"
    INACTIVE_PORT=8082
    info "배포 모드: 무중단 배포 (blue 실행 중 → green 으로 전환)"

elif [[ "$BLUE_RUNNING" == "false" && "$GREEN_RUNNING" == "true" ]]; then
    DEPLOY_MODE="bluegreen"
    ACTIVE="green"
    INACTIVE="blue"
    INACTIVE_PORT=8081
    info "배포 모드: 무중단 배포 (green 실행 중 → blue 으로 전환)"

else
    DEPLOY_MODE="bluegreen"
    warn "blue/green 둘 다 실행 중 → nginx upstream.conf 기준으로 inactive 판단"
    CURRENT_ACTIVE=$(_get_active_slot)
    if [[ "$CURRENT_ACTIVE" == "blue" ]]; then
        ACTIVE="blue";  INACTIVE="green"; INACTIVE_PORT=8082
    else
        ACTIVE="green"; INACTIVE="blue";  INACTIVE_PORT=8081
    fi
    info "  현재 활성: $ACTIVE → 배포 대상: $INACTIVE"
fi

# ── 비활성 슬롯 기동 ────────────────────────────────────────────────────
info "슬롯 [$INACTIVE] 기동 중..."
docker compose -f "$COMPOSE_APP" \
    --env-file "$ENV_FILE" \
    up -d --no-deps --force-recreate "file-gateway-$INACTIVE"

# ── 헬스체크 대기 ──────────────────────────────────────────────────────
info "헬스체크 대기 중 (최대 $((HEALTH_RETRIES * HEALTH_INTERVAL))초) → http://${HEALTH_HOST}:${INACTIVE_PORT}/api/health"

HEALTH_OK=false
for i in $(seq 1 $HEALTH_RETRIES); do
    HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
        "http://${HEALTH_HOST}:${INACTIVE_PORT}/api/health" 2>/dev/null || echo "000")
    if [[ "$HTTP_STATUS" == "200" ]]; then
        info "헬스체크 성공 (${i}/${HEALTH_RETRIES}회)"
        HEALTH_OK=true
        break
    fi
    warn "  대기 중... ($i/${HEALTH_RETRIES}) HTTP=$HTTP_STATUS"
    sleep "$HEALTH_INTERVAL"
done

if [[ "$HEALTH_OK" != "true" ]]; then
    error "헬스체크 실패! 슬롯 [$INACTIVE] 을 중지합니다."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$INACTIVE" || true
    exit 1
fi

# ── Nginx upstream 전환 (docker cp 방식) ────────────────────────────────
_update_upstream "$INACTIVE" "$IMAGE_TAG"

sleep 2

# ── 이전 슬롯 중지 (무중단 배포 시에만) ────────────────────────────────
if [[ "$DEPLOY_MODE" == "bluegreen" && -n "$ACTIVE" ]]; then
    if _is_running "file-gateway-$ACTIVE"; then
        info "이전 슬롯 [$ACTIVE] 중지 중..."
        docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$ACTIVE"
    else
        info "이전 슬롯 [$ACTIVE] 은 이미 중지 상태"
    fi
fi

# ── 프론트엔드 배포 ────────────────────────────────────────────────────
info "프론트엔드 배포 중..."
docker compose -f "$COMPOSE_APP" \
    --env-file "$ENV_FILE" \
    up -d --no-deps --force-recreate file-gateway-frontend

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "배포 완료!"
info "  모드      : $DEPLOY_MODE"
info "  활성 슬롯 : $INACTIVE"
info "  이미지 태그: $IMAGE_TAG"
info "  서비스 URL : http://localhost"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
