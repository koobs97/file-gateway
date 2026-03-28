#!/usr/bin/env bash
# =====================================================================
# Blue-Green 무중단 배포 스크립트
#
# 동작 원리:
#  1. upstream.conf 파일로 현재 활성 슬롯(blue/green) 판단
#  2. 비활성 슬롯에 신규 이미지 배포 (docker-compose up --force-recreate)
#  3. 헬스체크 통과 후 nginx upstream.conf를 교체
#  4. nginx -s reload → 무중단(graceful) 전환
#  5. 이전 슬롯 중지
#
# 사용법:
#  ./docker/scripts/deploy.sh [IMAGE_TAG]
#  ./docker/scripts/deploy.sh v1.2.3
#
# 사전 조건:
#  - docker / docker-compose 설치
#  - docker/docker-compose.infra.yml 기반 인프라 실행 중
#  - docker/.env.prod 파일 존재
# =====================================================================
set -euo pipefail

# ── 경로 설정 ────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$DOCKER_DIR/.." && pwd)"

COMPOSE_APP="$DOCKER_DIR/docker-compose.app.yml"
ENV_FILE="$DOCKER_DIR/.env.prod"
UPSTREAM_CONF="$DOCKER_DIR/nginx/conf.d/upstream.conf"

IMAGE_TAG="${1:-latest}"
HEALTH_RETRIES=30      # 최대 30회 (60초)
HEALTH_INTERVAL=2      # 2초 간격

# ── 색상 출력 ────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[DEPLOY]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 환경 변수 파일 확인 ──────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
    error ".env.prod 파일이 없습니다: $ENV_FILE"
    error "docker/.env.prod.example 을 복사해 설정하세요."
    exit 1
fi

export IMAGE_TAG
# .env.prod 값 로드 (export로 docker-compose가 읽을 수 있게)
set -a; source "$ENV_FILE"; set +a
export IMAGE_TAG  # source 후 덮어쓰기 (파일의 IMAGE_TAG보다 인자 우선)

info "배포 시작 — 이미지 태그: $IMAGE_TAG"

# ── 현재 활성 슬롯 판단 ──────────────────────────────────────────────
if grep -q "file-gateway-blue" "$UPSTREAM_CONF" 2>/dev/null; then
    ACTIVE="blue";   INACTIVE="green"
    INACTIVE_PORT=8082
else
    ACTIVE="green";  INACTIVE="blue"
    INACTIVE_PORT=8081
fi
info "현재 활성: $ACTIVE  →  배포 대상: $INACTIVE (포트 $INACTIVE_PORT)"

# ── 비활성 슬롯 시작 (신규 이미지 강제 재생성) ──────────────────────
info "슬롯 $INACTIVE 시작 중..."
docker compose -f "$COMPOSE_APP" \
    --env-file "$ENV_FILE" \
    up -d --no-deps --force-recreate "file-gateway-$INACTIVE"

# ── 헬스체크 대기 ────────────────────────────────────────────────────
info "헬스체크 대기 중 (최대 $((HEALTH_RETRIES * HEALTH_INTERVAL))초)..."
HEALTH_OK=false
for i in $(seq 1 $HEALTH_RETRIES); do
    HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
        "http://localhost:${INACTIVE_PORT}/api/health" 2>/dev/null || echo "000")
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

# nginx graceful reload (진행 중인 요청 완료 후 새 upstream 적용)
docker exec file-gateway-nginx nginx -s reload
info "Nginx reload 완료 → 트래픽이 $INACTIVE 로 전환되었습니다."

# 안정화 대기
sleep 3

# ── 이전 슬롯 중지 ──────────────────────────────────────────────────
if docker ps --format '{{.Names}}' | grep -q "file-gateway-$ACTIVE"; then
    info "이전 슬롯 $ACTIVE 중지 중..."
    docker compose -f "$COMPOSE_APP" --env-file "$ENV_FILE" stop "file-gateway-$ACTIVE"
fi

# ── 프론트엔드 배포 (간단 재시작, 상태 없음) ────────────────────────
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
