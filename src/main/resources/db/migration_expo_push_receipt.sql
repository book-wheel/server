-- Expo Push Ticket을 저장하고 배달 Receipt를 후속 조회하기 위한 테이블을 추가한다.
-- 대상 DB: dev, prod 각각
-- Hibernate ddl-auto=update가 테이블을 만들 수 있지만, 배포 전 스키마를 명시적으로 반영하려면 이 SQL을 실행한다.

CREATE TABLE IF NOT EXISTS expo_push_receipt (
    receipt_id VARCHAR(100) NOT NULL,
    expo_push_token VARCHAR(255) NOT NULL,
    notification_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (receipt_id),
    INDEX idx_expo_push_receipt_created_at (created_at)
);
