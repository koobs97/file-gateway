# 구현 단계별 체크리스트

## Phase 1: 기반 인프라 구축
- [ ] Docker Compose를 활용한 PostgreSQL 환경 설정
- [ ] Spring Boot 3 기본 프로젝트 구조 생성 (Multi-module 또는 DDD 패키지)
- [ ] JPA Entity 및 Repository 구현 (File, Log, Event)
- [ ] 공통 응답 포맷 및 예외 처리(GlobalExceptionHandler) 구현

## Phase 2: 파일 업로드 및 저장소 (Storage)
- [ ] Storage 인터페이스 정의 및 LocalFileSystem 구현
- [ ] Streaming 방식의 파일 업로드 API 구현
- [ ] 파일 확장자 및 MIME Type 검증 로직 추가

## Phase 3: 핵심 CDR 엔진 (Apache POI)
- [ ] Office 문서 분석기 구현 (매크로 탐지)
- [ ] Office 문서 무해화 처리기 구현 (매크로/링크 제거 후 재생성)
- [ ] 단위 테스트 작성 (샘플 악성 파일을 이용한 제거 확인)

## Phase 4: 비동기 처리 및 워커
- [ ] Spring Async 또는 내부 Queue 기반 Worker 구현
- [ ] 상태 업데이트 로직 구현 (Processing -> Done/Fail)
- [ ] WebSocket을 이용한 실시간 상태 전송 구현

## Phase 5: 관리자 콘솔 (Frontend)
- [ ] Vue 3 + Element Plus 기본 레이아웃 구성
- [ ] 파일 업로드 UI 및 드래그 앤 드롭 구현
- [ ] 파일 처리 이력 목록 및 대시보드(통계) 구현
- [ ] Swagger(OpenAPI) 연동

## Phase 6: 고도화 및 보안
- [ ] API Key 기반 외부 연동 인증 추가
- [ ] Spring Cloud Gateway 기본 설정
- [ ] Jenkins 파이프라인(CI/CD) 구성