-- 회원 탈퇴 보존 시각과 별도 동의 증빙 테이블을 추가한다.
-- 운영 DB에서 애플리케이션 배포 전에 한 번 실행한다.

SET @withdrawal_requested_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'withdrawal_requested_at'
);
SET @withdrawal_requested_at_ddl = IF(
    @withdrawal_requested_at_count = 0,
    'ALTER TABLE users ADD COLUMN withdrawal_requested_at DATETIME(6) NULL',
    'DO 0'
);
PREPARE withdrawal_requested_at_statement FROM @withdrawal_requested_at_ddl;
EXECUTE withdrawal_requested_at_statement;
DEALLOCATE PREPARE withdrawal_requested_at_statement;

SET @purge_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'purge_at'
);
SET @purge_at_ddl = IF(
    @purge_at_count = 0,
    'ALTER TABLE users ADD COLUMN purge_at DATETIME(6) NULL',
    'DO 0'
);
PREPARE purge_at_statement FROM @purge_at_ddl;
EXECUTE purge_at_statement;
DEALLOCATE PREPARE purge_at_statement;

SET @purge_at_index_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_purge_at'
);
SET @purge_at_index_ddl = IF(
    @purge_at_index_count = 0,
    'CREATE INDEX idx_users_purge_at ON users (purge_at)',
    'DO 0'
);
PREPARE purge_at_index_statement FROM @purge_at_index_ddl;
EXECUTE purge_at_index_statement;
DEALLOCATE PREPARE purge_at_index_statement;

SET @created_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'created_at'
);
SET @created_at_ddl = IF(
    @created_at_count = 0,
    'ALTER TABLE users ADD COLUMN created_at DATETIME(6) NULL',
    'DO 0'
);
PREPARE created_at_statement FROM @created_at_ddl;
EXECUTE created_at_statement;
DEALLOCATE PREPARE created_at_statement;

SET @onboarding_index_count = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_onboarding_cleanup'
);
SET @onboarding_index_ddl = IF(
    @onboarding_index_count = 0,
    'CREATE INDEX idx_users_onboarding_cleanup ON users (is_active, is_profile_set, created_at)',
    'DO 0'
);
PREPARE onboarding_index_statement FROM @onboarding_index_ddl;
EXECUTE onboarding_index_statement;
DEALLOCATE PREPARE onboarding_index_statement;

CREATE TABLE IF NOT EXISTS user_consent_history (
    consent_record_pk BIGINT NOT NULL AUTO_INCREMENT,
    user_pk VARCHAR(50) NOT NULL,
    subject_identifier_hash CHAR(64) NOT NULL,
    subject_identifier_type VARCHAR(30) NOT NULL,
    hash_algorithm VARCHAR(30) NOT NULL,
    hash_key_version VARCHAR(30) NOT NULL,
    terms_agreed BOOLEAN NOT NULL,
    terms_version VARCHAR(50) NOT NULL,
    terms_document_hash CHAR(64) NOT NULL,
    privacy_agreed BOOLEAN NOT NULL,
    privacy_version VARCHAR(50) NOT NULL,
    privacy_document_hash CHAR(64) NOT NULL,
    marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_version VARCHAR(50) NULL,
    marketing_document_hash CHAR(64) NULL,
    agreed_at DATETIME(6) NOT NULL,
    consent_source VARCHAR(30) NOT NULL,
    retention_started_at DATETIME(6) NULL,
    retention_until DATETIME(6) NULL,
    PRIMARY KEY (consent_record_pk),
    INDEX idx_consent_history_user_pk (user_pk),
    INDEX idx_consent_history_subject_hash (subject_identifier_hash),
    INDEX idx_consent_history_retention_until (retention_until)
);

-- 이전 마이그레이션 초안을 실행한 DB의 컬럼명도 일반화한다.
SET @consent_retention_started_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_consent_history'
      AND COLUMN_NAME = 'retention_started_at'
);
SET @consent_withdrawal_requested_at_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_consent_history'
      AND COLUMN_NAME = 'withdrawal_requested_at'
);
SET @consent_retention_started_at_ddl = IF(
    @consent_retention_started_at_count = 0 AND @consent_withdrawal_requested_at_count > 0,
    'ALTER TABLE user_consent_history CHANGE COLUMN withdrawal_requested_at retention_started_at DATETIME(6) NULL',
    IF(
        @consent_retention_started_at_count = 0,
        'ALTER TABLE user_consent_history ADD COLUMN retention_started_at DATETIME(6) NULL',
        'DO 0'
    )
);
PREPARE consent_retention_started_at_statement FROM @consent_retention_started_at_ddl;
EXECUTE consent_retention_started_at_statement;
DEALLOCATE PREPARE consent_retention_started_at_statement;

-- 프로필 미완료 계정을 7일 후 삭제해도 동의 증빙은 삭제하지 않는다.
-- 앱이 보관 시작·만료 시각을 설정하고, 만료된 증빙만 보유 정리 스케줄러가 삭제한다.

-- 탈퇴자가 직접 삭제하지 않은 공개 콘텐츠는 남기고 회원과의 연결만 해제한다.
-- MySQL의 UNIQUE 인덱스는 NULL을 여러 건 허용하므로 익명 리뷰 간 (book_info_id, user_id) 충돌이 없다.
ALTER TABLE post
    MODIFY COLUMN user_id VARCHAR(50) NULL;

ALTER TABLE post_comment
    MODIFY COLUMN user_id VARCHAR(50) NULL;

ALTER TABLE book_review
    MODIFY COLUMN user_id VARCHAR(50) NULL;

-- 탈퇴자가 소유했던 도서와 다른 회원의 독서 기록을 보존하기 위한 익명 소유자다.
INSERT IGNORE INTO users (
    id, login_id, password, nickname, mail, social_type, is_active, is_profile_set, created_at
) VALUES (
    'SYSTEM_DELETED_OWNER',
    '__system_deleted_owner__',
    'LOGIN_DISABLED',
    '탈퇴한 사용자',
    'deleted-owner@invalid.local',
    'NONE',
    FALSE,
    TRUE,
    NOW(6)
);

-- DB 참조를 먼저 삭제한 뒤에도 외부 저장소 삭제를 안전하게 재시도할 수 있도록 객체 키를 보존한다.
CREATE TABLE IF NOT EXISTS s3_deletion_task (
    task_pk BIGINT NOT NULL AUTO_INCREMENT,
    user_pk VARCHAR(50) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    object_key_hash CHAR(64) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    PRIMARY KEY (task_pk),
    UNIQUE INDEX uk_s3_deletion_task_object_hash (object_key_hash),
    INDEX idx_s3_deletion_task_next_attempt (next_attempt_at)
);
