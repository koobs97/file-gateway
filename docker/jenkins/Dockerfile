# =====================================================================
# Jenkins + Docker CLI 이미지
# - 기반: jenkins/jenkins:lts-jdk17 (Jenkins LTS + JDK17)
# - 추가: Docker CLI (호스트 Docker 데몬 제어용)
#         docker compose plugin
#
# 왜 Docker CLI가 필요한가?
#   Jenkins가 파이프라인에서 'docker build', 'docker compose' 명령을 실행해야 함.
#   실제 Docker 데몬은 호스트(Docker Desktop)에 있고,
#   컨테이너는 /var/run/docker.sock 소켓을 통해 호스트 데몬과 통신.
# =====================================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# Docker CLI + Compose Plugin 설치
RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && chmod a+r /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/debian \
        $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update -qq \
    && apt-get install -y --no-install-recommends \
        docker-ce-cli \
        docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# jenkins 사용자를 docker 그룹에 추가
# (호스트의 docker.sock 파일의 그룹 GID와 맞춰야 할 수 있음)
RUN groupadd -f docker && usermod -aG docker jenkins

USER jenkins
