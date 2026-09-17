-- 사용자 계정 전체에 적용되는 채팅 알림 수신 설정을 추가한다.
-- 기존 사용자와 신규 사용자는 기본적으로 채팅 알림을 수신한다.

SET @chat_enabled_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification_preference'
      AND COLUMN_NAME = 'chat_enabled'
);
SET @chat_enabled_column_ddl = IF(
    @chat_enabled_column_count = 0,
    'ALTER TABLE notification_preference ADD COLUMN chat_enabled BOOLEAN NULL DEFAULT TRUE AFTER community_enabled',
    'DO 0'
);
PREPARE chat_enabled_column_statement FROM @chat_enabled_column_ddl;
EXECUTE chat_enabled_column_statement;
DEALLOCATE PREPARE chat_enabled_column_statement;

UPDATE notification_preference
SET chat_enabled = TRUE
WHERE chat_enabled IS NULL;

ALTER TABLE notification_preference
    MODIFY COLUMN chat_enabled BOOLEAN NOT NULL DEFAULT TRUE;
