# =====================================================================
# File Gateway Frontend - Multi-stage Dockerfile
# Stage 1 (builder): Node.js로 Vue 3 Vite 빌드
# Stage 2 (runtime): Nginx로 정적 파일 서빙
# =====================================================================

# ----- Stage 1: Build -----
FROM node:20-alpine AS builder
WORKDIR /app

# 의존성 레이어 캐싱
COPY package*.json ./
RUN npm ci --quiet

# 소스 복사 후 프로덕션 빌드
COPY . .
RUN npm run build

# ----- Stage 2: Nginx Runtime -----
FROM nginx:alpine AS runtime

# 빌드 결과물 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# Vue Router history mode + SPA fallback 설정
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
