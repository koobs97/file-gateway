#!/usr/bin/env bash
# =====================================================================
# Blue-Green 무중단 배포 스크립트
#
# 배포 모드 자동 판단 (컨테이너 실행 상태 기반):
#   1. 초기 배포  : blue/green 모두 없음 → blue 직접 기동
#   2. 무중단 배포: blue만 실행 중       → green 기동 후 트래픽 전환
#                   green만 실행 중      → blue 기동 후 트래픽 전환
#                   둘 다 실행 중        → upstream.conf 기준으로 inactive 교체
#
# 인프라 충돌 처리:
#   - postgres/nginx 컨테이너가 이미 존재하면(로컬 개발용 포함) 재사용
#   - docker compose up을 통한 이름 충돌 없이 네트워크만 연결
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_INFRA="$DOCKER_DIR/docker-compose.infra.yml"
COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
UPSTREAM_CONF="$DOCKER_DIR/nginx/conf.d/upstream.conf"

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

# ── 인프라 자동 기동 ───────────────────────────────────────────────────
# 로컬 개발용 컨테이너(postgres 등)가 이미 실행 중일 수 있으므로
# docker compose up 대신 inspect → start → network connect 순서로 처리
info "인프라 상태 확인 중..."

# 1. 네트워크 생성 (이미 있으면 무시)
if docker network inspect "file-gateway-net" > /dev/null 2>&1; then
    info "  네트워크 이미 존재: file-gateway-net"
else
    docker network create file-gateway-net
    info "  네트워크 생성: file-gateway-net"
fi

# 2. postgres 처리
#    로컬 개발 인프라로 이미 떠있어도 이름 충돌 없이 재사용
_ensure_container() {
    local NAME="$1"
    local COMPOSE_SVC="$2"

    if docker inspect "$NAME" > /dev/null 2>&1; then
        # 이미 존재하는 컨테이너: 기동 + 네트워크 연결
        local STATUS
        STATUS=$(docker inspect --format='{{.State.Status}}' "$NAME" 2>/dev/null || echo "")
        if [[ "$STATUS" != "running" ]]; then
            docker start "$NAME" 2>/dev/null || true
        fi
        docker network connect file-gateway-net "$NAME" 2>/dev/null || true
        info "  $NAME: 기존 컨테이너 재사용 (상태: $STATUS)"
    else
        # 존재하지 않으면 docker compose로 신규 생성
        # --no-deps: depends_on으로 인한 다른 컨테이너 자동 기동 방지 (이름 충돌 방지)
        docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d --no-deps "$COMPOSE_SVC"
        info "  $NAME: 신규 생성"
    fi
}

_ensure_container "file-gateway-postgres" "postgres"

# 3. nginx 처리 (postgres 보다 나중에 기동)
_ensure_container "file-gateway-nginx" "nginx"

# nginx가 새로 생성된 경우 설정 로드 대기
sleep 2

# ── 앱 컨테이너 실행 상태 확인 ─────────────────────────────────────────
_is_running() {
    local NAME="$1"
    local STATUS
    STATUS=$(docker inspect --format='{{.State.Status}}' "$NAME" 2>/dev/null || echo "")
    [[ "$STATUS" == "running" ]]
}

BLUE_RUNNING=false
GREEN_RUNNING=false
_is_running "file-gateway-blue"  && BLUE_RUNNING=true
_is_running "file-gateway-green" && GREEN_RUNNING=true

info "컨테이너 상태 — blue: $BLUE_RUNNING / green: $GREEN_RUNNING"

# ── 배포 모드 결정 ──────────────────────────────────────────────────────
#   INACTIVE : 새로 기동할 슬롯
#   INACTIVE_PORT : 헬스체크용 호스트 포트
#   ACTIVE   : 현재 트래픽을 받고 있는 슬롯 (초기 배포 시에는 없음)
DEPLOY_MODE=""
ACTIVE=""
INACTIVE=""
INACTIVE_PORT=0

if [[ "$BLUE_RUNNING" == "false" && "$GREEN_RUNNING" == "false" ]]; then
    # ── 초기 배포 ──────────────────────────────────────────────────────
    DEPLOY_MODE="initial"
    INACTIVE="blue"
    INACTIVE_PORT=8081
    info "배포 모드: 초기 배포 → blue 슬롯 기동"

elif [[ "$BLUE_RUNNING" == "true" && "$GREEN_RUNNING" == "false" ]]; then
    # ── 무중단 배포: blue → green ──────────────────────────────────────
    DEPLOY_MODE="bluegreen"
    ACTIVE="blue"
    INACTIVE="green"
    INACTIVE_PORT=8082
    info "배포 모드: 무중단 배포 (blue 실행 중 → green 으로 전환)"

elif [[ "$BLUE_RUNNING" == "false" && "$GREEN_RUNNING" == "true" ]]; then
    # ── 무중단 배포: green → blue ──────────────────────────────────────
    DEPLOY_MODE="bluegreen"
    ACTIVE="green"
    INACTIVE="blue"
    INACTIVE_PORT=8081
    info "배포 모드: 무중단 배포 (green 실행 중 → blue 으로 전환)"

else
    # ── 둘 다 실행 중 (비정상 / 재배포) ──────────────────────────────
    DEPLOY_MODE="bluegreen"
    warn "blue/green 둘 다 실행 중 → upstream.conf 기준으로 inactive 판단"
    if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
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
# Jenkins는 컨테이너 내부에서 실행 → host.docker.internal 로 호스트 포트 접근
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

# ── Nginx upstream 전환 ─────────────────────────────────────────────────
info "Nginx upstream → file-gateway-${INACTIVE}:8080"
cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (deploy.sh에 의해 자동 갱신됨)
# 현재 활성: $INACTIVE (이미지 태그: $IMAGE_TAG)
upstream backend {
    server file-gateway-${INACTIVE}:8080;
}
EOF

docker exec file-gateway-nginx nginx -s reload
info "Nginx reload 완료 → 트래픽이 [$INACTIVE] 로 전환되었습니다."

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
