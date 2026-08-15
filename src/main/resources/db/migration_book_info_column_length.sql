-- book_info 의 도서 메타데이터 컬럼 길이를 외부 API 원본이 들어갈 만큼 늘린다.
--
-- 배경   : 찜 시점에 저장하는 제목/저자/표지는 알라딘 응답이나 인기대출 도서 값을 그대로 쓴다.
--          두 원본 모두 book_info 보다 긴 값을 허용해, 정상 데이터에서도 UPDATE 가 실패할 수 있었다.
--            · popular_loan_book : title 500 / author 500 / thumbnail 1000
--            · book_info(변경 전): title 255 / author 100 / cover_image 255
--          알라딘 저자 필드 실측(173종) 결과 최대 504자, 100자 초과가 8건(4.6%)이었다.
--          "안나 밀보른 (지은이), 호밀로스 (그림), 김지선 (옮긴이)" 처럼 역할이 나열되기 때문이다.
--
-- 실행 시점: 이 커밋을 배포하기 전에 실행한다.
--          ddl-auto=update 는 기존 컬럼의 길이를 바꾸지 않으므로, 애플리케이션만 배포하면
--          엔티티 정의와 DB 가 어긋난 채로 남는다.
-- 대상 DB : dev, prod 각각
-- 주의    : 길이만 넓히는 변경이라 기존 데이터는 그대로 유지된다.


-- 1) 현재 정의 확인 -------------------------------------------------------
-- NULL 허용 여부와 문자셋이 아래 문장과 다르면, 확인한 정의에 맞춰 수정해 실행한다.
SHOW CREATE TABLE book_info;


-- 2) 컬럼 길이 확장 -------------------------------------------------------
ALTER TABLE book_info MODIFY COLUMN title VARCHAR(500) NULL;
ALTER TABLE book_info MODIFY COLUMN author VARCHAR(1000) NULL;
ALTER TABLE book_info MODIFY COLUMN cover_image VARCHAR(1000) NULL;


-- 3) 적용 확인 ------------------------------------------------------------
-- CHARACTER_MAXIMUM_LENGTH 가 각각 500 / 1000 / 1000 으로 나오면 완료다.
SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'book_info'
  AND COLUMN_NAME IN ('title', 'author', 'cover_image');
