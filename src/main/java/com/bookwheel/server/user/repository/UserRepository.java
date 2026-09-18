package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    @Query("""
            select u
            from User u
            where u.isActive = false
              and u.purgeAt is not null
              and u.purgeAt <= :now
              and (
                    :cursorPurgeAt is null
                    or u.purgeAt > :cursorPurgeAt
                    or (u.purgeAt = :cursorPurgeAt and u.id > :cursorUserPK)
              )
            order by u.purgeAt asc, u.id asc
            """)
    List<User> findPurgeCandidates(
            @Param("now") LocalDateTime now,
            @Param("cursorPurgeAt") LocalDateTime cursorPurgeAt,
            @Param("cursorUserPK") String cursorUserPK,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            select u
            from User u
            where u.isActive = true
              and u.isProfileSet = false
              and u.createdAt is not null
              and u.createdAt <= :cutoff
              and (
                    :cursorCreatedAt is null
                    or u.createdAt > :cursorCreatedAt
                    or (u.createdAt = :cursorCreatedAt and u.id > :cursorUserPK)
              )
            order by u.createdAt asc, u.id asc
            """)
    List<User> findAbandonedOnboardingCandidates(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorUserPK") String cursorUserPK,
            org.springframework.data.domain.Pageable pageable
    );
}
