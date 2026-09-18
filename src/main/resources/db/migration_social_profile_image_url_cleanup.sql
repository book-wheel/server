-- 소셜 로그인이 users.profile_image_key 에 저장한 외부 프로필 URL 정리.
--
-- 배경    : profile_image_key 는 S3 objectKey 컬럼이지만 기존 소셜 신규 로직이
--           구글 picture, 카카오 thumbnail URL 을 그대로 저장했다.
--           이 값을 S3 key 로 서명하면 존재하지 않는 객체의 URL 이 생성된다.
--
-- 실행 시점: migration_image_url_to_object_key.sql 을 먼저 실행한 후 한 번 실행한다.
-- 대상 DB : dev, prod 각각
-- 주의    : UPDATE 전에 아래 SELECT 결과가 소셜 외부 URL 인지 확인한다.
--           소셜 타입과 공급자 CDN 호스트가 모두 일치하는 행만 정리하며,
--           여러 번 실행해도 결과가 같다(멱등).

SELECT id, social_type, profile_image_key
FROM users
WHERE (social_type = 'GOOGLE' AND (
           LOWER(profile_image_key) LIKE 'https://%.googleusercontent.com/%'
        OR LOWER(profile_image_key) LIKE 'http://%.googleusercontent.com/%'
      ))
   OR (social_type = 'KAKAO' AND (
           LOWER(profile_image_key) LIKE 'https://%.kakaocdn.net/%'
        OR LOWER(profile_image_key) LIKE 'http://%.kakaocdn.net/%'
      ))
LIMIT 20;

UPDATE users
SET profile_image_key = NULL
WHERE (social_type = 'GOOGLE' AND (
           LOWER(profile_image_key) LIKE 'https://%.googleusercontent.com/%'
        OR LOWER(profile_image_key) LIKE 'http://%.googleusercontent.com/%'
      ))
   OR (social_type = 'KAKAO' AND (
           LOWER(profile_image_key) LIKE 'https://%.kakaocdn.net/%'
        OR LOWER(profile_image_key) LIKE 'http://%.kakaocdn.net/%'
      ));

SELECT id, social_type, profile_image_key AS unrecognized_profile_image_url
FROM users
WHERE profile_image_key LIKE 'http%';
