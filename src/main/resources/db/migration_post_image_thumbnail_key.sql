-- 갤러리 대표 이미지용 썸네일 objectKey 컬럼 추가.
--
-- 배경    : 갤러리 목록이 업로드된 원본 이미지를 그대로 내려주고 있었다.
--           랩 서버 실측으로 대표 이미지 1장이 1,857,647 바이트(1.86MB)였고,
--           기본 페이지 18장 기준 한 화면에 약 33MB 가 전송된다.
--           축소본을 따로 만들어 저장하기 위해 post_images 에 컬럼을 추가한다.
--
-- 실행 시점: 이 커밋을 배포해 애플리케이션을 기동하면 ddl-auto=update 가 컬럼을 만들어 준다.
--           배포 전에 스키마를 먼저 반영하려면 아래 1) 을 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 컬럼은 NULL 을 허용한다. 썸네일 도입 이전에 올라온 행은 값이 비어 있고,
--           조회 경로가 원본(fileExtensions)으로 폴백하므로 이미지가 깨지지 않는다.
--           기존 행의 썸네일 생성(백필)은 별도 작업에서 다룬다.

-- 0) 사전 점검 -------------------------------------------------------------
-- 컬럼이 이미 있으면 1) 은 건너뛴다(ddl-auto=update 로 이미 만들어졌을 수 있다).
SELECT COLUMN_NAME, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'post_images'
  AND COLUMN_NAME = 'thumbnail_key';


-- 1) 컬럼 추가 -------------------------------------------------------------
ALTER TABLE post_images
    ADD COLUMN thumbnail_key VARCHAR(500) NULL COMMENT '갤러리 목록용 축소본 objectKey. NULL 이면 원본으로 폴백';


-- 2) 확인 ------------------------------------------------------------------
-- 아직 백필 전이라 thumbnail_key 는 전부 NULL 인 것이 정상이다.
SELECT COUNT(*) AS total_rows,
       SUM(thumbnail_key IS NULL) AS rows_without_thumbnail
FROM post_images;
