-- Expo Push Token이 한 사용자에게만 귀속되도록 중복을 정리하고 유니크 제약을 추가한다.
-- 대상 DB: dev, prod 각각
-- 실행 순서: 1번 조회 -> 중복이 있으면 2번 -> 3번
-- 주의: notification_preference.fcm_token 컬럼명은 무중단 배포를 위해 유지하지만 값은 Expo Push Token이다.


-- 1) 같은 Expo Push Token이 여러 사용자에게 등록돼 있는지 확인한다. ------------------
SELECT fcm_token, COUNT(*) AS cnt, GROUP_CONCAT(user_pk ORDER BY preference_id) AS user_pks
FROM notification_preference
WHERE fcm_token IS NOT NULL
GROUP BY fcm_token
HAVING cnt > 1;


-- 2) 중복이 있을 때만 실행한다. 가장 최근에 생성된 preference만 유지한다. --------
UPDATE notification_preference old_preference
JOIN notification_preference latest_preference
  ON latest_preference.fcm_token = old_preference.fcm_token
 AND latest_preference.preference_id > old_preference.preference_id
SET old_preference.fcm_token = NULL
WHERE old_preference.fcm_token IS NOT NULL;


-- 3) 유니크 제약을 추가한다. ---------------------------------------------------
-- 이미 유니크 인덱스가 있다면 ALTER TABLE은 다시 실행하지 않는다.
SHOW INDEX FROM notification_preference WHERE Key_name = 'uk_notification_pref_expo_token';

ALTER TABLE notification_preference
    ADD CONSTRAINT uk_notification_pref_expo_token UNIQUE (fcm_token);
