// =====================================================================
// File Gateway - Jenkins Pipeline (Blue-Green 무중단 배포)
//
// 트리거: main 브랜치에 push 이벤트 발생 시 자동 실행
//
// 파이프라인 단계:
//  1. Checkout      : 코드 체크아웃
//  2. Test          : 백엔드 유닛 테스트 (Docker 컨테이너 내)
//  3. Build Images  : Backend + Frontend 이미지 병렬 빌드
//  4. Deploy        : Blue-Green 무중단 배포
//  5. Smoke Test    : 배포 완료 후 기본 동작 확인
//
// 사전 조건:
//  - Jenkins 서버에 Docker, docker-compose 설치
//  - docker/.env.prod 파일이 Jenkins 워크스페이스에 존재
//    (Jenkins Credentials 또는 사전 배치로 관리)
//  - docker-compose.infra.yml 기반 인프라(postgres, nginx) 실행 중
// =====================================================================
pipeline {
    agent any

    environment {
        // Git 커밋 SHA 앞 7자리를 이미지 태그로 사용 (추적 가능성)
        IMAGE_TAG = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        // docker-compose 프로젝트 이름
        COMPOSE_PROJECT_NAME = "file-gateway"
    }

    // main 브랜치 push 시 자동 트리거
    triggers {
        githubPush()
    }

    options {
        // 빌드 타임아웃 20분
        timeout(time: 20, unit: 'MINUTES')
        // 최근 5개 빌드 이력만 보관
        buildDiscarder(logRotator(numToKeepStr: '5'))
        // 동일 브랜치 동시 빌드 방지
        disableConcurrentBuilds()
    }

    stages {

        // ── Stage 1: Checkout ────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                echo "빌드 커밋: ${env.GIT_COMMIT} (태그: ${IMAGE_TAG})"
            }
        }

        // ── Stage 2: Test ────────────────────────────────────────────
        // Jenkins 컨테이너에 JDK17 내장 → 직접 Gradle 실행 (Docker-in-Docker 경로 문제 회피)
        stage('Test') {
            steps {
                dir('backend') {
                    sh 'chmod +x gradlew && ./gradlew test --no-daemon -q'
                }
            }
            post {
                always {
                    // 테스트 결과 JUnit 리포트 수집
                    junit allowEmptyResults: true,
                          testResults: 'backend/build/test-results/**/*.xml'
                }
                failure {
                    echo '테스트 실패! 배포를 중단합니다.'
                }
            }
        }

        // ── Stage 3: Build Docker Images ─────────────────────────────
        // 백엔드 / 프론트엔드 이미지를 병렬로 빌드
        stage('Build Images') {
            parallel {

                stage('Build Backend') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                    -t file-gateway-backend:${IMAGE_TAG} \
                                    -t file-gateway-backend:latest \
                                    --build-arg BUILDKIT_INLINE_CACHE=1 \
                                    .
                            """
                        }
                    }
                }

                stage('Build Frontend') {
                    steps {
                        dir('frontend') {
                            sh """
                                docker build \
                                    -t file-gateway-frontend:${IMAGE_TAG} \
                                    -t file-gateway-frontend:latest \
                                    .
                            """
                        }
                    }
                }
            }
        }

        // ── Stage 4: Deploy (Blue-Green) ─────────────────────────────
        // Jenkins Credentials에서 .env.prod 파일을 주입 후 배포 스크립트 실행
        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'koobs97-docker-env', variable: 'ENV_PROD_FILE')]) {
                    sh '''
                        cp "$ENV_PROD_FILE" docker/.env.prod
                        chmod +x docker/scripts/deploy.sh docker/scripts/rollback.sh
                    '''
                    sh "docker/scripts/deploy.sh ${IMAGE_TAG}"
                }
            }
        }

        // ── Stage 5: Smoke Test ──────────────────────────────────────
        // 배포 완료 후 기본 엔드포인트 응답 확인
        stage('Smoke Test') {
            steps {
                sh '''
                    echo "스모크 테스트 시작..."
                    sleep 5

                    # 헬스체크 엔드포인트
                    HEALTH=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost/api/health 2>/dev/null || echo "000")
                    if [ "$HEALTH" != "200" ]; then
                        echo "스모크 테스트 실패: /api/health → HTTP $HEALTH"
                        exit 1
                    fi

                    # Swagger 접근 가능 여부
                    SWAGGER=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost/swagger-ui/index.html 2>/dev/null || echo "000")
                    echo "Swagger UI: HTTP $SWAGGER"

                    echo "스모크 테스트 통과! (health=$HEALTH)"
                '''
            }
        }
    }

    // ── 후처리 ──────────────────────────────────────────────────────
    post {
        success {
            echo """
            ====================================
            배포 성공!
              커밋  : ${env.GIT_COMMIT}
              태그  : ${IMAGE_TAG}
              브랜치: ${env.GIT_BRANCH}
            ====================================
            """
            // TODO: Slack / 이메일 알림 추가 가능
        }
        failure {
            echo '배포 실패! 자동 롤백을 시도합니다...'
            withCredentials([file(credentialsId: 'koobs97-docker-env', variable: 'ENV_PROD_FILE')]) {
                sh '''
                    cp "$ENV_PROD_FILE" docker/.env.prod || true
                    chmod +x docker/scripts/rollback.sh
                    docker/scripts/rollback.sh || echo "롤백 스크립트 실패 - 수동 복구 필요"
                '''
            }
        }
        always {
            // 미사용 이미지 정리 (디스크 절약)
            sh 'docker image prune -f --filter "until=24h" || true'
        }
    }
}
