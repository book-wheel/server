-- 관심 도서 목록에서 표지와 저자를 내려주기 위해 찜 시점에 도서 정보를 BookInfo 에 저장하도록 바꾸면서,
-- 이미 찜한 도서의 저자/표지를 백필한다.
--
-- 실행 시점: 이 커밋을 배포해 애플리케이션을 기동하면 ddl-auto=update 가
--           book_info.author, book_info.cover_image 컬럼을 자동 생성한다. 그 직후 아래 SQL 을 한 번 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 백필하지 않아도 관심 도서 목록은 book 테이블을 함께 확인하므로 동작에 문제는 없다.
--           백필은 표지가 비어 보이는 관심 도서 수를 줄이기 위한 것이다.
--           백필로도 채워지지 않는 도서는 사용자가 찜을 다시 누르는 시점에 알라딘 API 값으로 채워진다.

-- 모임 도서로 등록되며 만들어진 book 행에서 저자와 표지를 가져와 채운다.
UPDATE book_info bi
JOIN book b ON b.isbn = bi.isbn
SET bi.author = COALESCE(bi.author, b.author),
    bi.cover_image = COALESCE(bi.cover_image, b.cover_image)
WHERE bi.author IS NULL
   OR bi.cover_image IS NULL;

-- 제목도 같은 기준으로 채워 둔다. (관심 도서 목록은 BookInfo 값을 우선 사용한다)
UPDATE book_info bi
JOIN book b ON b.isbn = bi.isbn
SET bi.title = b.title
WHERE bi.title IS NULL
  AND b.title IS NOT NULL;