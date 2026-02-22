package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByMail(String mail);
    Optional<User> findByNickname(String nickname);
    boolean existsByUserId(String userId);
    boolean existsByMail(String mail);
    boolean existsByNickname(String nickname);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
    Optional<User> findByMailAndSocialType(String mail, SocialType socialType);
}

