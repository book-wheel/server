package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByLoginId(String loginId);
    boolean existsByNickname(String nickname);
    boolean existsByProfileImageKey(String profileImageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userPK")
    Optional<User> findByUserPKForUpdate(@Param("userPK") String userPK);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
    Optional<User> findByMailAndSocialType(String mail, SocialType socialType);

    // 이메일 + 가입경로(SocialType) + 활성상태(isActive) 모두 만족하는 단 한 명의 유저 조회
    Optional<User> findByMailAndSocialTypeAndIsActiveTrue(String mail, SocialType socialType);

    // 이메일 + 가입경로(SocialType) + 활성상태(isActive) 만족하는 유저의 존재 여부만 확인
    boolean existsByMailAndSocialTypeAndIsActiveTrue(String mail, SocialType socialType);
}
