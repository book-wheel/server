-- 프로필 이미지 교체·삭제 후 기존 key가 다른 계정에서 참조 중인지 확인하는 조회를 위한 인덱스다.
-- Hibernate ddl-auto=update가 기존 테이블의 인덱스 생성을 보장하지 않는 환경에서는 배포 전 적용한다.
CREATE INDEX idx_users_profile_image_key ON users (profile_image_key);
