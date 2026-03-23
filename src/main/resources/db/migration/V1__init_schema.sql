-- file 테이블
CREATE TABLE file (
    id              BIGSERIAL PRIMARY KEY,
    original_name   VARCHAR(255)  NOT NULL,
    stored_name     VARCHAR(255)  NOT NULL,
    file_size       BIGINT        NOT NULL,
    mime_type       VARCHAR(100),
    storage_path    TEXT          NOT NULL,
    storage_type    VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',
    status          VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED',
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- file_process_log 테이블
CREATE TABLE file_process_log (
    id          BIGSERIAL PRIMARY KEY,
    file_id     BIGINT        NOT NULL REFERENCES file(id),
    step        VARCHAR(20)   NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    message     TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- file_event 테이블
CREATE TABLE file_event (
    id          BIGSERIAL PRIMARY KEY,
    file_id     BIGINT        NOT NULL REFERENCES file(id),
    event_type  VARCHAR(20)   NOT NULL,
    payload     JSONB,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- users 테이블
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    role        VARCHAR(20)   NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- api_client 테이블
CREATE TABLE api_client (
    id          BIGSERIAL PRIMARY KEY,
    client_name VARCHAR(100)  NOT NULL,
    api_key     VARCHAR(255)  NOT NULL UNIQUE,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_file_status     ON file(status);
CREATE INDEX idx_file_created_at ON file(created_at);
CREATE INDEX idx_event_file_id   ON file_event(file_id);
CREATE INDEX idx_log_file_id     ON file_process_log(file_id);
