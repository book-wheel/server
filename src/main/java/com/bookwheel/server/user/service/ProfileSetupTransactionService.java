package com.bookwheel.server.user.service;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.LoginResponse;
import com.bookwheel.server.user.dto.ProfileSetupRequest;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileSetupTransactionService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public Result persist(
            String userPK,
            ProfileSetupRequest request,
            ProfileImageUpdate profileImageUpdate
    ) {
        User user = findByUserPKForUpdateAndValidateActive(userPK);
        String previousProfileImageKey = user.getProfileImageKey();

        String newNickname = request.nickname();
        if (newNickname != null && !newNickname.isBlank()) {
            if (!user.getNickname().equals(newNickname)
                    && userRepository.existsByNickname(newNickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
        } else {
            newNickname = user.getNickname();
        }

        String resolvedProfileImageKey = profileImageUpdate.resolve(previousProfileImageKey);
        user.updateProfile(newNickname, request.comment(), resolvedProfileImageKey);
        user.completeProfile();

        String accessToken = jwtTokenProvider.createAccessToken(userPK, AuthRole.USER);
        String refreshToken = jwtTokenProvider.createRefreshToken(userPK, AuthRole.USER);
        refreshTokenRepository.save(new RefreshToken(userPK, refreshToken));

        log.info("프로필 설정 DB 반영 완료: userPK={}, nickname={}, imageKey={}",
                userPK, user.getNickname(), resolvedProfileImageKey);

        return new Result(
                LoginResponse.of(user, accessToken, refreshToken),
                previousProfileImageKey
        );
    }

    private User findByUserPKForUpdateAndValidateActive(String userPK) {
        User user = userRepository.findByUserPKForUpdate(userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }
        if (!"ACTIVE".equals(user.getBanStatus())) {
            throw new BusinessException(ErrorCode.BANNED_USER);
        }
        return user;
    }

    public record ProfileImageUpdate(
            Type type,
            String finalObjectKey
    ) {
        public ProfileImageUpdate {
            boolean keyRequired = type == Type.RETAIN_IF_CURRENT || type == Type.REPLACE;
            if (type == null || (keyRequired && finalObjectKey == null)
                    || (!keyRequired && finalObjectKey != null)) {
                throw new IllegalArgumentException("프로필 이미지 변경 정보가 유효하지 않습니다.");
            }
        }

        public static ProfileImageUpdate retain() {
            return new ProfileImageUpdate(Type.RETAIN, null);
        }

        public static ProfileImageUpdate delete() {
            return new ProfileImageUpdate(Type.DELETE, null);
        }

        public static ProfileImageUpdate retainIfCurrent(String expectedCurrentObjectKey) {
            return new ProfileImageUpdate(Type.RETAIN_IF_CURRENT, expectedCurrentObjectKey);
        }

        public static ProfileImageUpdate replace(String finalObjectKey) {
            return new ProfileImageUpdate(Type.REPLACE, finalObjectKey);
        }

        private String resolve(String currentObjectKey) {
            return switch (type) {
                case RETAIN -> currentObjectKey;
                case RETAIN_IF_CURRENT -> {
                    if (!Objects.equals(currentObjectKey, finalObjectKey)) {
                        throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
                    }
                    yield currentObjectKey;
                }
                case DELETE -> null;
                case REPLACE -> finalObjectKey;
            };
        }

        public enum Type {
            RETAIN,
            RETAIN_IF_CURRENT,
            DELETE,
            REPLACE
        }
    }

    public record Result(
            LoginResponse response,
            String previousProfileImageKey
    ) {
    }
}
