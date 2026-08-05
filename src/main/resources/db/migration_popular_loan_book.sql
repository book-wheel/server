-- 정보나루 인기대출도서 스냅샷 저장 테이블.
--
-- source/start_date/end_date 기준으로 적재 구간을 구분하고,
-- 같은 구간 안에서는 ISBN별로 하나의 인기 점수만 유지한다.
-- Java 엔티티의 rank 필드는 MySQL 예약어 충돌을 피하기 위해 ranking 컬럼에 매핑한다.

CREATE TABLE IF NOT EXISTS popular_loan_book (
    popular_loan_book_id BIGINT NOT NULL AUTO_INCREMENT,
    isbn VARCHAR(20) NOT NULL,
    ranking INT NOT NULL,
    loan_count INT NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    source VARCHAR(30) NOT NULL,
    PRIMARY KEY (popular_loan_book_id),
    UNIQUE KEY uk_popular_loan_book_snapshot (isbn, source, start_date, end_date),
    KEY idx_popular_loan_book_isbn (isbn),
    KEY idx_popular_loan_book_snapshot (source, start_date, end_date)
);
