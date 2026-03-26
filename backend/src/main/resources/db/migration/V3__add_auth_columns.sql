-- users 테이블 인증 관련 컬럼 추가
ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN refresh_token VARCHAR(512);
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP;
