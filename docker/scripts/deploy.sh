#!/usr/bin/env bash
# =====================================================================
# Blue-Green 무중단 배포 스크립트
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

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[DEPLOY]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 환경 변수 확인 ────────────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
    error ".env.prod 파일이 없습니다: $ENV_FILE"
    exit 1
fi

set -a; source "$ENV_FILE"; set +a
export IMAGE_TAG  # 인자값 우선

info "배포 시작 — 이미지 태그: $IMAGE_TAG"

# ── 인프라 자동 기동 ──────────────────────────────────────────────────
# 컨테이너가 이미 존재하는 경우 docker compose up 시 이름 충돌 방지
# → 네트워크/컨테이너를 개별적으로 처리
info "인프라 상태 확인 중..."

# 1. 네트워크 생성 (이미 있으면 무시)
docker network create file-gateway-net 2>/dev/null \
    && info "  네트워크 생성: file-gateway-net" \
    || info "  네트워크 이미 존재: file-gateway-net"

# 2. postgres: 이미 존재하면 start + 네트워크 연결, 없으면 compose로 신규 생성
if docker ps -a --format '{{.Names}}' | grep -q "^file-gateway-postgres$"; then
    docker start file-gateway-postgres 2>/dev/null || true
    docker network connect file-gateway-net file-gateway-postgres 2>/dev/null || true
    info "  postgres: 기존 컨테이너 사용"
else
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d postgres
    info "  postgres: 신규 생성"
fi

# 3. nginx: 이미 존재하면 start, 없으면 compose로 신규 생성
if docker ps -a --format '{{.Names}}' | grep -q "^file-gateway-nginx$"; then
    docker start file-gateway-nginx 2>/dev/null || true
    docker network connect file-gateway-net file-gateway-nginx 2>/dev/null || true
    info "  nginx: 기존 컨테이너 사용"
else
    docker compose -f "$COMPOSE_INFRA" --env-file "$ENV_FILE" up -d nginx
    sleep 3
    info "  nginx: 신규 생성"
fi

# ── 현재 활성 슬롯 판단 ──────────────────────────────────────────────
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";   INACTIVE="green"
    INACTIVE_PORT=8082
else
    ACTIVE="green";  INACTIVE="blue"
    INACTIVE_PORT=8081
fi
info "현재 활성: $ACTIVE  →  배포 대상: $INACTIVE (포트 $INACTIVE_PORT)"

# ── 비활성 슬롯 시작 ──────────────────────────────────────────────────
info "슬롯 $INACTIVE 시작 중..."
docker compose -f "$COMPOSE_APP" \
    --env-file "$ENV_FILE" \
    up -d --no-deps --force-recreate "file-gateway-$INACTIVE"

# ── 헬스체크 대기 ────────────────────────────────────────────────────
# Jenkins는 컨테이너 내부에서 실행되므로 host.docker.internal로 호스트 접근
# (Docker Desktop for Windows/Mac 환경)
HEALTH_HOST="${HEALTH_HOST:-host.docker.internal}"
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
    error "헬스체크 실패! 슬롯 $INACTIVE 를 중지합니다."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$INACTIVE" || true
    exit 1
fi

# ── Nginx upstream 전환 ──────────────────────────────────────────────
info "Nginx upstream 전환: $ACTIVE → $INACTIVE"
cat > "$UPSTREAM_CONF" <<EOF
# Blue-Green 업스트림 (deploy.sh에 의해 자동 갱신됨)
# 현재 활성: $INACTIVE
upstream backend {
    server file-gateway-${INACTIVE}:8080;
}
EOF

docker exec file-gateway-nginx nginx -s reload
info "Nginx reload 완료 → 트래픽이 $INACTIVE 로 전환되었습니다."

sleep 3

# ── 이전 슬롯 중지 ───────────────────────────────────────────────────
if docker ps --format '{{.Names}}' | grep -q "file-gateway-$ACTIVE"; then
    info "이전 슬롯 $ACTIVE 중지 중..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$ACTIVE"
fi

# ── 프론트엔드 배포 ───────────────────────────────────────────────────
info "프론트엔드 배포 중..."
docker compose -f "$COMPOSE_APP" \
    --env-file "$ENV_FILE" \
    up -d --no-deps --force-recreate file-gateway-frontend

info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "배포 완료!"
info "  활성 슬롯 : $INACTIVE"
info "  이미지 태그: $IMAGE_TAG"
info "  서비스 URL : http://localhost"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
