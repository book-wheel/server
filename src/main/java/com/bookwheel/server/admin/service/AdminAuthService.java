package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.dto.AdminLoginRequest;
import com.bookwheel.server.admin.dto.AdminLoginResponse;
import com.bookwheel.server.admin.entity.Admin;
import com.bookwheel.server.admin.repository.AdminRepository;
import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_ADMIN);
        }

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtTokenProvider.createAccessToken(admin.getAdminPK(), AuthRole.ADMIN);
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getAdminPK(), AuthRole.ADMIN);

        refreshTokenRepository.save(new RefreshToken(admin.getAdminPK(), refreshToken));
        log.info("관리자 로그인 완료: adminPK={}", admin.getAdminPK());

        return AdminLoginResponse.of(admin, accessToken, refreshToken);
    }
}
