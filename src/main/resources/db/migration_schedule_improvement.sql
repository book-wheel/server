-- 목표 인원 기반 일정 생성 및 모집 중 책 배정 저장을 위한 데이터 마이그레이션.
--
-- 실행 시점: 새 서버 코드 배포 전에 dev/prod DB에서 한 번 실행한다.
-- 기존 라운드가 있는 일정은 라운드 수 + 1을 기존 목표 인원으로 사용한다.
-- 라운드가 없는 일정은 다음 일정 생성 요청에서 목표 인원을 입력받도록 NULL로 유지한다.

SET @schedule_target_column_count = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'reading_group'
      AND COLUMN_NAME = 'target_member_count'
);
SET @schedule_target_column_ddl = IF(
    @schedule_target_column_count = 0,
    'ALTER TABLE reading_group ADD COLUMN target_member_count INT NULL',
    'DO 0'
);
PREPARE schedule_target_column_statement FROM @schedule_target_column_ddl;
EXECUTE schedule_target_column_statement;
DEALLOCATE PREPARE schedule_target_column_statement;

UPDATE reading_group g
SET target_member_count = (
    SELECT COUNT(*) + 1
    FROM round r
    WHERE r.group_id = g.group_id
)
WHERE EXISTS (
    SELECT 1
    FROM round r
    WHERE r.group_id = g.group_id
)
  AND g.target_member_count IS NULL;

-- 모집 중에 미리 계산한 책 배정을 저장할 수 있도록 기존 MySQL ENUM에 PLANNED를 추가한다.
ALTER TABLE wheel_state
    MODIFY COLUMN wheel_state ENUM (
        'PLANNED',
        'WAITING',
        'READY',
        'READING',
        'COMPLETED',
        'UNFINISHED'
    ) NOT NULL;
