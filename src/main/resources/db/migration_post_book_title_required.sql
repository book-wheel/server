-- 게시글 도서 제목을 post.book_title 필수 컬럼으로 분리한다.
--
-- 실행 시점: 배포 전에 기존 커뮤니티 도서 데이터를 삭제할 예정이면 아래 순서대로 데이터를 먼저 비운 뒤,
--           애플리케이션 배포 또는 ALTER TABLE을 진행한다.
-- 대상 DB : dev, prod 각각
--
-- 삭제 대상은 book_info를 참조하는 커뮤니티 도서 데이터와 그 하위 데이터다.
-- users, reading_group, member, book, own_book 등 사용자/모임/모임도서 데이터는 삭제 대상이 아니다.

DELETE FROM notification
WHERE type IN ('POST_LIKED', 'POST_COMMENTED', 'REVIEW_LIKED');

DELETE FROM review_like;

DELETE FROM post_comment;
DELETE FROM post_like;
DELETE FROM post_report;
DELETE FROM post_images;

DELETE FROM book_vote;
DELETE FROM book_review;
DELETE FROM book_like;
DELETE FROM post;

DELETE FROM book_info;

-- ddl-auto=update를 쓰지 않거나, 배포 전에 명시적으로 컬럼을 추가하려면 실행한다.
-- Hibernate ddl-auto=update가 이미 post.book_title을 생성했다면 이 문장은 실행하지 않는다.
ALTER TABLE post ADD COLUMN book_title VARCHAR(255) NOT NULL;
