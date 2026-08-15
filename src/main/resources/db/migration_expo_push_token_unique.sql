-- 기존 DB를 유지하면 FCM 컬럼을 Expo Push 컬럼으로 변경하고,
-- Expo Push Token이 한 사용자에게만 귀속되도록 중복을 정리한 뒤 유니크 제약을 추가한다.
-- DB를 새로 생성하는 경우에는 JPA 스키마가 expo_push_token을 생성하므로 이 SQL을 실행할 필요가 없다.
-- 대상 DB: 기존 dev, prod DB
-- 실행 순서: 1번 컬럼 변경 -> 2번 조회 -> 중복이 있으면 3번 -> 4번

-- 1) 기존 FCM 컬럼명을 실제 저장 값에 맞게 변경한다. -----------------------
ALTER TABLE notification_preference
    RENAME COLUMN fcm_token TO expo_push_token;

-- 2) 같은 Expo Push Token이 여러 사용자에게 등록돼 있는지 확인한다. ------------------
SELECT expo_push_token, COUNT(*) AS cnt, GROUP_CONCAT(user_pk ORDER BY preference_id) AS user_pks
FROM notification_preference
WHERE expo_push_token IS NOT NULL
GROUP BY expo_push_token
HAVING cnt > 1;


-- 3) 중복이 있을 때만 실행한다. 가장 최근에 생성된 preference만 유지한다. --------
UPDATE notification_preference old_preference
JOIN notification_preference latest_preference
  ON latest_preference.expo_push_token = old_preference.expo_push_token
 AND latest_preference.preference_id > old_preference.preference_id
SET old_preference.expo_push_token = NULL
WHERE old_preference.expo_push_token IS NOT NULL;


-- 4) 유니크 제약을 추가한다. ---------------------------------------------------
-- 이미 유니크 인덱스가 있다면 ALTER TABLE은 다시 실행하지 않는다.
SHOW INDEX FROM notification_preference WHERE Key_name = 'uk_notification_pref_expo_token';

ALTER TABLE notification_preference
    ADD CONSTRAINT uk_notification_pref_expo_token UNIQUE (expo_push_token);
