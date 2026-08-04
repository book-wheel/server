-- 게시물 상세의 도서 제목을 BookInfo에 저장하도록 바꾸면서, 기존 게시물의 제목을 백필한다.
--
-- 실행 시점: 이 커밋을 배포해 애플리케이션을 기동하면 ddl-auto=update 가
--           book_info.title 컬럼을 자동 생성한다. 그 직후 아래 SQL 을 한 번 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 백필하지 않아도 상세 조회는 book 테이블을 한 번 더 확인하므로 동작에 문제는 없다.
--           백필은 그 추가 조회를 없애고, 제목을 찾지 못하는 게시물 수를 줄이기 위한 것이다.

-- 그룹 도서로 등록되며 만들어진 book 행에서 제목을 가져와 채운다.
UPDATE book_info bi
JOIN book b ON b.isbn = bi.isbn
SET bi.title = b.title
WHERE bi.title IS NULL
  AND b.title IS NOT NULL;
