-- 정보나루 인기대출도서 스냅샷 저장 테이블. (현재 스키마 전체)
--
-- 실행 시점: popular_loan_book 테이블이 아직 없는 신규 환경에서 한 번 실행한다.
--           ddl-auto=update 로 기동하면 Hibernate 가 동일한 테이블을 만들어 주므로,
--           스키마를 Hibernate 밖에서 관리할 때만 명시적으로 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 아래 CREATE TABLE 에는 migration_popular_loan_book_metadata.sql 이 추가하는
--           서지 메타데이터 컬럼(title, author, publisher, published_date, thumbnail)과
--           idx_popular_loan_book_title 인덱스가 이미 포함되어 있다.
--           따라서 이 파일을 실행한 환경에서는 metadata 스크립트를 실행하지 않는다.
--           (Duplicate column name / Duplicate key name 으로 실패한다.)
--
-- source/start_date/end_date 기준으로 적재 구간을 구분하고,
-- 같은 구간 안에서는 ISBN별로 하나의 인기 점수만 유지한다.
-- Java 엔티티의 rank 필드는 MySQL 예약어 충돌을 피하기 위해 ranking 컬럼에 매핑한다.

CREATE TABLE IF NOT EXISTS popular_loan_book (
    popular_loan_book_id BIGINT NOT NULL AUTO_INCREMENT,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(500),
    author VARCHAR(500),
    publisher VARCHAR(200),
    published_date VARCHAR(20),
    thumbnail VARCHAR(1000),
    ranking INT NOT NULL,
    loan_count INT NOT NULL,
    collected_at DATETIME(6) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    source VARCHAR(30) NOT NULL,
    PRIMARY KEY (popular_loan_book_id),
    UNIQUE KEY uk_popular_loan_book_snapshot (isbn, source, start_date, end_date),
    KEY idx_popular_loan_book_isbn (isbn),
    KEY idx_popular_loan_book_snapshot (source, start_date, end_date),
    KEY idx_popular_loan_book_title (title)
);
