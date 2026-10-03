CREATE TABLE apple_oauth_credential (
    user_pk VARCHAR(50) NOT NULL,
    encrypted_refresh_token VARCHAR(4096) NOT NULL,
    encryption_key_version VARCHAR(20) NOT NULL,
    revocation_requested BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    PRIMARY KEY (user_pk),
    INDEX idx_apple_oauth_credential_revocation_due (revocation_requested, next_attempt_at)
);

-- users 테이블과 외래키를 두지 않는다.
-- 계정 삭제 뒤에도 Apple token 폐기가 성공할 때까지 재시도 정보를 보존해야 한다.
