package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUserId(String userId);
    boolean existsByNickname(String nickname);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
    Optional<User> findByMailAndSocialType(String mail, SocialType socialType);

    // 이메일 + 가입경로(SocialType) + 활성상태(isActive) 모두 만족하는 단 한 명의 유저 조회
    Optional<User> findByMailAndSocialTypeAndIsActiveTrue(String mail, SocialType socialType);
}

