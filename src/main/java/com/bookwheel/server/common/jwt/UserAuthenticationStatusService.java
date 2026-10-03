package com.bookwheel.server.common.jwt;

import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAuthenticationStatusService {

    private final UserRepository userRepository;
    private final Clock clock;

    public boolean canAuthenticate(String userPK) {
        return userRepository.findById(userPK)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .filter(user -> "ACTIVE".equals(user.getBanStatus(LocalDateTime.now(clock))))
                .isPresent();
    }
}
