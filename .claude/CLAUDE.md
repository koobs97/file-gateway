# File Gateway (CDR 기반) 프로젝트 가이드

## 프로젝트 개요
- **목적**: MS Office 파일 내 위험 요소(매크로, 링크)를 제거하는 CDR(Content Disarm and Reconstruction) 시스템
- **핵심 가치**: 원본 파일 재구성을 통한 Zero-day 공격 방지

## 기술 스택
- **Backend**: Java 17, Spring Boot 3.x, Spring Data JPA, Apache POI
- **Frontend**: Vue 3 (Vite), Pinia, Element Plus
- **Database**: PostgreSQL (JSONB 활용)
- **Infra**: Docker, Jenkins

## 빌드 및 실행 명령어
### Backend
- 빌드: `./gradlew build`
- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`

### Frontend
- 설치: `npm install`
- 실행: `npm run dev`
- 빌드: `npm run build`

## 코드 스타일 규칙
- **Java**: Google Java Style Guide 준수, DDD 기반 패키지 구조
- **Vue**: Composition API (Script Setup) 사용
- **API**: RESTful API 준수, 모든 응답은 공통 포맷(`success`, `data`, `error`) 사용
- **파일명**: Backend는 PascalCase, Frontend는 kebab-case/PascalCase 혼용

## 주요 제약 사항
1. 원본 파일은 수정하지 않고 항상 새로운 무해화 파일을 생성한다.
2. 대용량 파일 처리를 위해 메모리 적재를 최소화하고 스트리밍 방식을 사용한다.
3. 비동기 처리를 기본으로 하여 대기 시간을 최소화한다.