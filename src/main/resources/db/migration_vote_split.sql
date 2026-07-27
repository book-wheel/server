-- 추천/비추천을 리뷰(book_review)에서 투표(book_vote) 테이블로 분리하는 데이터 마이그레이션.
--
-- 실행 시점: 커밋 2 코드를 배포해 애플리케이션을 기동하면 ddl-auto=update 가
--           book_vote 테이블을 자동 생성한다. 그 직후 아래 SQL 을 한 번 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 2번(DROP COLUMN)을 실행하기 전까지는 새 리뷰 작성이 실패할 수 있으므로
--           배포 직후 지체 없이 함께 실행한다.

-- 1) 기존 리뷰의 추천/비추천 값을 투표 테이블로 백필
INSERT INTO book_vote (book_info_id, user_id, is_recommended, created_at)
SELECT book_info_id, user_id, is_recommended, created_at
FROM book_review;

-- 2) 리뷰 테이블에서 더 이상 쓰지 않는 추천 컬럼 제거
ALTER TABLE book_review DROP COLUMN is_recommended;
