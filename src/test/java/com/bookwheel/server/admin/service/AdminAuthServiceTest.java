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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @InjectMocks
    private AdminAuthService adminAuthService;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("관리자 로그인 성공 - ADMIN 권한 토큰 발급")
    void login_Success() {
        Admin admin = Admin.builder()
                .loginId("admin")
                .password("encoded")
                .name("관리자")
                .isActive(true)
                .build();
        AdminLoginRequest request = new AdminLoginRequest("admin", "password");

        when(adminRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(admin.getAdminPK(), AuthRole.ADMIN)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(admin.getAdminPK(), AuthRole.ADMIN)).thenReturn("refresh");

        AdminLoginResponse response = adminAuthService.login(request);

        assertEquals(admin.getAdminPK(), response.adminPK());
        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("관리자 로그인 실패 - 계정 없음")
    void login_Fail_AdminNotFound() {
        when(adminRepository.findByLoginId("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminAuthService.login(new AdminLoginRequest("missing", "password")));

        assertEquals(ErrorCode.ADMIN_NOT_FOUND, exception.getErrorCode());
        verify(refreshTokenRepository, never()).save(any());
    }
}
