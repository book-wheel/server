-- 검색 결과에 정보나루 인기대출도서를 병합하기 위한 서지 메타데이터 컬럼을 추가한다.
--
-- 실행 시점: 서지 메타데이터 컬럼이 없던 시점(feat: add popular loan book persistence)의
--           popular_loan_book 테이블이 이미 적용된 기존 환경에서만 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 신규 환경은 migration_popular_loan_book.sql 의 CREATE TABLE 에 아래 컬럼과
--           인덱스가 모두 포함되어 있으므로 이 파일을 실행하지 않는다.
--           ddl-auto=update 로 기동해 Hibernate 가 이미 컬럼을 생성한 경우에도 실행하지 않는다.
--           일부만 존재하는 상태라면 이미 적용된 문장은 건너뛰고 나머지만 실행한다.
--           (MySQL 은 ADD COLUMN IF NOT EXISTS 를 지원하지 않아 문장별로 분리해 두었다.)

ALTER TABLE popular_loan_book ADD COLUMN title VARCHAR(500) NULL;
ALTER TABLE popular_loan_book ADD COLUMN author VARCHAR(500) NULL;
ALTER TABLE popular_loan_book ADD COLUMN publisher VARCHAR(200) NULL;
ALTER TABLE popular_loan_book ADD COLUMN published_date VARCHAR(20) NULL;
ALTER TABLE popular_loan_book ADD COLUMN thumbnail VARCHAR(1000) NULL;
ALTER TABLE popular_loan_book ADD INDEX idx_popular_loan_book_title (title);