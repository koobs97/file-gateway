# 시스템 아키텍처 및 상세 설계

## 1. 도메인 모델 (DDD)
- **File**: 파일 메타데이터 및 상태 관리
- **Process**: 무해화 로직 (Analyze, Sanitize), Worker 스케줄링
- **Storage**: 파일 저장소 추상화 (Local, S3)
- **User/Client**: 인증 및 외부 연동 관리

## 2. 데이터베이스 스키마 (PostgreSQL)
- `file`: 기본 정보 및 현재 상태 (`UPLOADED`, `PROCESSING`, `DONE`, `FAIL`)
- `file_process_log`: 단계별 처리 로그 (상세 에러 추적용)
- `file_event`: 상태 변경 이벤트 (WebSocket 연동용)
- `api_client`: 외부 연동용 API Key 관리

## 3. CDR 핵심 로직 (Apache POI)
- **검사 대상**: `.docx`, `.xlsx`, `.pptx` (OOXML 형식 우선)
- **제거 대상**:
    - VBA Macro (`vbaProject.bin`)
    - External Link / OLE Object
    - JavaScript (PDF 확장 시)

## 4. 비동기 워커 구조
- API Server: 파일 수신 후 DB 등록 및 Queue에 작업 전송
- Worker: Queue에서 작업을 가져와 POI 무해화 수행 후 저장소 업로드
- Notification: 완료 시 WebSocket을 통해 프론트엔드 알림 전송