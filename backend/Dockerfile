# =====================================================================
# File Gateway Backend - Multi-stage Dockerfile
# Stage 1 (builder): Gradle로 JAR 빌드
# Stage 2 (runtime): JRE 경량 이미지로 실행
# =====================================================================

# ----- Stage 1: Build -----
FROM gradle:8.5-jdk17-alpine AS builder
WORKDIR /app

# 의존성 레이어 캐싱: build.gradle / settings.gradle 먼저 복사
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# 소스 복사 후 JAR 빌드 (테스트는 CI 단계에서 별도 수행)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test -q

# ----- Stage 2: Runtime -----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# 보안: 전용 비-root 사용자 생성
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 파일 업로드 디렉토리 생성
RUN mkdir -p uploads && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

# prod 프로파일 활성화 (환경변수 DB_URL, JWT_SECRET 등 주입 필요)
ENTRYPOINT ["java", "-jar", \
    "-Dspring.profiles.active=prod", \
    "-Dfile.encoding=UTF-8", \
    "app.jar"]
