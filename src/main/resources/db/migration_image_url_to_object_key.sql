-- 이미지 저장 형식을 "전체 URL" 에서 "S3 objectKey" 로 바꾼 과거 변경들의 기존 데이터 보정.
--
-- 배경    : 아래 세 커밋에서 저장 형식이 바뀌었으나 데이터 보정 SQL 이 없었다.
--           - d6b5826  wheel_state_image : image_url 컬럼명은 그대로 두고 의미만 objectKey 로 변경
--           - 0730494  post_images        : image_url -> fileExtensions 로 컬럼명까지 변경
--           - 583002a  users              : profile_image -> profile_image_key 로 컬럼명까지 변경
--           조회 경로(S3Service.getPresignedGetUrl)는 저장값을 key 로 보고 그대로 서명하므로,
--           전환 이전에 저장된 행은 깨진 주소가 내려간다.
--           ddl-auto=update 는 컬럼을 추가만 하고 기존 값을 새 컬럼으로 옮겨주지 않는다.
--
-- 실행 시점: 이 커밋을 배포해 애플리케이션을 기동한 직후 한 번 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : 0) 사전 점검을 먼저 실행해 실제 값 형태와 대상 행 수를 확인한 뒤 1)~3) 을 실행한다.
--           UPDATE 는 되돌릴 수 없으므로 prod 는 실행 전에 대상 테이블을 덤프해 둔다.
--             mysqldump -u USER -p DB users post_images wheel_state_image > backup_before_objectkey.sql
--           변환 대상은 버킷 경로를 포함한 URL 값뿐이라 여러 번 실행해도 결과는 같다(멱등).
--           버킷 경로가 없는 외부 URL(소셜 로그인 프로필 등)은 건드리지 않고 4) 에서 따로 확인한다.

-- 버킷 경로 표식. dev/prod 모두 동일하나 버킷명이 다르면 이 줄만 바꾼다.
-- 예: https://s3.bookwheel.kr/book-wheel-images-purple-project/attachments/uuid_a.png?X-Amz-...
SET @bucket_marker = '/book-wheel-images-purple-project/';


-- 0) 사전 점검 -------------------------------------------------------------
-- 0-1) 옛 컬럼이 아직 남아 있는지 확인한다. 결과에 없는 컬럼에 대한 단계는 건너뛴다.
SELECT TABLE_NAME, COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'post_images' AND COLUMN_NAME = 'image_url')
    OR (TABLE_NAME = 'users' AND COLUMN_NAME = 'profile_image'));

-- 0-2) 보정 대상 행 수를 센다.
SELECT 'wheel_state_image' AS target, COUNT(*) AS rows_to_fix
FROM wheel_state_image
WHERE image_url LIKE 'http%' AND INSTR(image_url, @bucket_marker) > 0
UNION ALL
SELECT 'post_images', COUNT(*)
FROM post_images
WHERE (fileExtensions IS NULL OR fileExtensions = '')
UNION ALL
SELECT 'users', COUNT(*)
FROM users
WHERE profile_image_key LIKE 'http%' AND INSTR(profile_image_key, @bucket_marker) > 0;

-- 0-3) 실제 값 형태를 눈으로 확인한다(파일명이 %20 등으로 인코딩돼 있으면 아래 변환만으로는 부족하다).
SELECT image_url FROM wheel_state_image WHERE image_url LIKE 'http%' LIMIT 5;


-- 1) wheel_state_image : 같은 컬럼 안에서 URL -> objectKey 로 변환 -----------
UPDATE wheel_state_image
SET image_url = SUBSTRING(
        SUBSTRING_INDEX(image_url, '?', 1),
        INSTR(SUBSTRING_INDEX(image_url, '?', 1), @bucket_marker) + CHAR_LENGTH(@bucket_marker)
    )
WHERE image_url LIKE 'http%'
  AND INSTR(SUBSTRING_INDEX(image_url, '?', 1), @bucket_marker) > 0;


-- 2) post_images : 옛 image_url 컬럼의 값을 fileExtensions 로 백필 -----------
-- 0-1) 결과에 post_images.image_url 이 없으면 이 단계는 건너뛴다.
UPDATE post_images
SET fileExtensions = CASE
        WHEN INSTR(SUBSTRING_INDEX(image_url, '?', 1), @bucket_marker) > 0
            THEN SUBSTRING(
                     SUBSTRING_INDEX(image_url, '?', 1),
                     INSTR(SUBSTRING_INDEX(image_url, '?', 1), @bucket_marker) + CHAR_LENGTH(@bucket_marker)
                 )
        ELSE SUBSTRING_INDEX(image_url, '?', 1)
    END
WHERE (fileExtensions IS NULL OR fileExtensions = '')
  AND image_url IS NOT NULL
  AND image_url <> ''
  -- 우리 버킷 URL 이거나 이미 key 형태인 값만 옮긴다. 그 외 외부 URL 은 4-2) 에서 따로 확인한다.
  AND (image_url NOT LIKE 'http%' OR INSTR(SUBSTRING_INDEX(image_url, '?', 1), @bucket_marker) > 0);


-- 3) users : 옛 profile_image 컬럼의 값을 profile_image_key 로 백필 ----------
-- 0-1) 결과에 users.profile_image 가 없으면 이 단계는 건너뛴다.
-- 우리 버킷을 가리키는 값만 옮긴다. 소셜 로그인이 넣어 둔 외부 CDN URL 은 key 로 바꿀 수 없으므로 제외한다.
UPDATE users
SET profile_image_key = SUBSTRING(
        SUBSTRING_INDEX(profile_image, '?', 1),
        INSTR(SUBSTRING_INDEX(profile_image, '?', 1), @bucket_marker) + CHAR_LENGTH(@bucket_marker)
    )
WHERE (profile_image_key IS NULL OR profile_image_key = '')
  AND profile_image IS NOT NULL
  AND INSTR(SUBSTRING_INDEX(profile_image, '?', 1), @bucket_marker) > 0;

-- 새 컬럼에 이미 URL 형태로 들어간 값도 같은 규칙으로 정리한다.
UPDATE users
SET profile_image_key = SUBSTRING(
        SUBSTRING_INDEX(profile_image_key, '?', 1),
        INSTR(SUBSTRING_INDEX(profile_image_key, '?', 1), @bucket_marker) + CHAR_LENGTH(@bucket_marker)
    )
WHERE profile_image_key LIKE 'http%'
  AND INSTR(SUBSTRING_INDEX(profile_image_key, '?', 1), @bucket_marker) > 0;


-- 4) 남은 값 확인 ----------------------------------------------------------
-- 4-1) 변환되지 않고 남은 URL 값. 대부분 소셜 로그인 프로필(구글 picture, 카카오 thumbnail)이며,
--      우리 버킷의 객체가 아니라 SQL 로는 key 로 바꿀 수 없다.
--      CustomOAuth2UserService 가 외부 URL 을 profile_image_key 에 그대로 넣고 있어
--      코드 수정 없이는 다시 쌓이므로, 별도 이슈로 다룬다.
SELECT id, profile_image_key
FROM users
WHERE profile_image_key LIKE 'http%'
LIMIT 20;

-- 4-2) 보정 후 남은 URL 형태 행이 없어야 정상이다.
SELECT 'wheel_state_image' AS target, COUNT(*) AS url_rows_left
FROM wheel_state_image WHERE image_url LIKE 'http%'
UNION ALL
SELECT 'post_images', COUNT(*)
FROM post_images WHERE fileExtensions LIKE 'http%' OR fileExtensions IS NULL OR fileExtensions = '';
