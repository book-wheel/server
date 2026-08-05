-- 진행 중 멤버 변동 후 읽기 순서와 미래 일정을 단계적으로 재확정하기 위한 상태값.
SET @schedule_reconfiguration_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'reading_group'
      AND COLUMN_NAME = 'schedule_reconfiguration_status'
);
SET @schedule_reconfiguration_column_ddl = IF(
    @schedule_reconfiguration_column_count = 0,
    'ALTER TABLE reading_group ADD COLUMN schedule_reconfiguration_status VARCHAR(50) NULL',
    'DO 0'
);
PREPARE schedule_reconfiguration_column_statement FROM @schedule_reconfiguration_column_ddl;
EXECUTE schedule_reconfiguration_column_statement;
DEALLOCATE PREPARE schedule_reconfiguration_column_statement;

-- Hibernate가 컬럼을 먼저 추가한 환경도 안전하게 보정한 뒤 NOT NULL 제약을 적용한다.
UPDATE reading_group
SET schedule_reconfiguration_status = 'NONE'
WHERE schedule_reconfiguration_status IS NULL
   OR schedule_reconfiguration_status = '';

ALTER TABLE reading_group
    MODIFY COLUMN schedule_reconfiguration_status VARCHAR(50) NOT NULL DEFAULT 'NONE';
