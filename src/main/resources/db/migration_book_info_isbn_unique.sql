-- book_info.isbn 유니크 인덱스를 확인하고, 없으면 추가한다.
--
-- 배경   : 엔티티에는 45726bc "refactor : isbn에 유니크 제약 추가" 에서 @Column(unique = true) 가 붙었지만,
--          이미 만들어져 있던 컬럼에 유니크 제약을 붙이는 일은 ddl-auto=update 로는 보장되지 않는다.
--          Hibernate 는 유니크 제약 생성에 실패해도 경고만 남기고 조용히 넘어가므로,
--          제약이 실제로 걸렸는지는 DB 에서 직접 확인해야 한다.
-- 필요성 : 제약이 없으면 같은 ISBN 을 동시에 처음 등록할 때 book_info 행이 중복 생성되고,
--          이후 findByIsbn 이 두 행을 만나 조회 자체가 실패한다.
--          코드 쪽 findOrCreateByIsbn 의 업서트(on duplicate key update)도 이 인덱스가 있어야 동작한다.
-- 대상 DB : dev, prod 각각
-- 실행 시점: 배포 전후 무관. 1번을 먼저 확인하고 결과에 따라 2번 또는 3번을 실행한다.


-- 1) 현재 상태 확인 -------------------------------------------------------
-- (a) 유니크 인덱스가 이미 있는지 확인한다. NON_UNIQUE = 0 인 isbn 인덱스가 나오면 이미 적용된 상태다.
SHOW INDEX FROM book_info WHERE Column_name = 'isbn';

-- (b) 중복 ISBN 이 있는지 확인한다. 0건이면 3번으로 바로 간다.
SELECT isbn, COUNT(*) AS cnt, GROUP_CONCAT(book_info_id ORDER BY book_info_id) AS ids
FROM book_info
GROUP BY isbn
HAVING cnt > 1;


-- 2) 중복이 있을 때만 실행 (정리) -----------------------------------------
-- 주의: 아래는 각 ISBN 에서 가장 작은 book_info_id 를 남기고 나머지를 그 행으로 합친다.
--       참조 테이블(post, book_review, book_vote, book_like)을 함께 옮기므로 반드시 백업 후 실행한다.
--       book_review / book_vote / book_like 는 (book_info_id, user_id|user_pk) 유니크 제약이 있어,
--       같은 사용자가 중복된 두 book_info 에 각각 남긴 행이 있으면 옮기는 순간 충돌한다.
--       그래서 옮기기 전에 충돌하는 쪽을 먼저 지운다. (남는 쪽이 더 오래된 행이다)

-- 남길 대표 행을 정한다.
CREATE TEMPORARY TABLE tmp_book_info_canonical AS
SELECT isbn, MIN(book_info_id) AS keep_id
FROM book_info
GROUP BY isbn
HAVING COUNT(*) > 1;

-- 옮겼을 때 유니크 제약과 충돌하는 행을 먼저 제거한다.
DELETE dup
FROM book_review dup
JOIN book_info bi ON bi.book_info_id = dup.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
JOIN book_review keep ON keep.book_info_id = c.keep_id AND keep.user_id = dup.user_id
WHERE dup.book_info_id <> c.keep_id;

DELETE dup
FROM book_vote dup
JOIN book_info bi ON bi.book_info_id = dup.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
JOIN book_vote keep ON keep.book_info_id = c.keep_id AND keep.user_id = dup.user_id
WHERE dup.book_info_id <> c.keep_id;

DELETE dup
FROM book_like dup
JOIN book_info bi ON bi.book_info_id = dup.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
JOIN book_like keep ON keep.book_info_id = c.keep_id AND keep.user_pk = dup.user_pk
WHERE dup.book_info_id <> c.keep_id;

-- 남은 참조를 대표 행으로 옮긴다.
UPDATE post p
JOIN book_info bi ON bi.book_info_id = p.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
SET p.book_info_id = c.keep_id
WHERE p.book_info_id <> c.keep_id;

UPDATE book_review r
JOIN book_info bi ON bi.book_info_id = r.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
SET r.book_info_id = c.keep_id
WHERE r.book_info_id <> c.keep_id;

UPDATE book_vote v
JOIN book_info bi ON bi.book_info_id = v.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
SET v.book_info_id = c.keep_id
WHERE v.book_info_id <> c.keep_id;

UPDATE book_like l
JOIN book_info bi ON bi.book_info_id = l.book_info_id
JOIN tmp_book_info_canonical c ON c.isbn = bi.isbn
SET l.book_info_id = c.keep_id
WHERE l.book_info_id <> c.keep_id;

-- 대표 행에 비어 있는 제목/저자/표지가 있으면 중복 행에서 가져와 채운다.
UPDATE book_info keep
JOIN tmp_book_info_canonical c ON c.keep_id = keep.book_info_id
JOIN book_info dup ON dup.isbn = c.isbn AND dup.book_info_id <> c.keep_id
SET keep.title = COALESCE(keep.title, dup.title),
    keep.author = COALESCE(keep.author, dup.author),
    keep.cover_image = COALESCE(keep.cover_image, dup.cover_image);

-- 참조가 모두 옮겨진 중복 행을 지운다.
DELETE dup
FROM book_info dup
JOIN tmp_book_info_canonical c ON c.isbn = dup.isbn
WHERE dup.book_info_id <> c.keep_id;

DROP TEMPORARY TABLE tmp_book_info_canonical;

-- 1-(b) 를 다시 실행해 0건인지 확인한 뒤 3번으로 넘어간다.


-- 3) 유니크 인덱스 추가 ---------------------------------------------------
-- 1-(a) 에서 이미 유니크 인덱스가 확인됐다면 실행하지 않는다. (중복 이름으로 실패한다)
ALTER TABLE book_info
    ADD CONSTRAINT uk_book_info_isbn UNIQUE (isbn);
