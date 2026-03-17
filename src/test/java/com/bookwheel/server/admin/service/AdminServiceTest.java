package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.dto.AdminBanRequest;
import com.bookwheel.server.admin.dto.AdminBanResponse;
import com.bookwheel.server.admin.dto.BanReason;
import com.bookwheel.server.admin.dto.PenaltyResponse;
import com.bookwheel.server.admin.entity.Penalty;
import com.bookwheel.server.admin.repository.PenaltyRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PenaltyRepository penaltyRepository;

    @Test
    @DisplayName("유저 밴 성공 - 정상적으로 유저를 제재하고 패널티 이력을 저장한다.")
    void banUser_Success() {
        // given
        String userPk = "user123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ABUSIVE_LANGUAGE, null);

        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userPk);
        when(mockUser.getRole()).thenReturn(Role.USER); // 일반 유저
        when(mockUser.getIsActive()).thenReturn(true); // 활성 상태
        when(mockUser.getNickname()).thenReturn("테스트유저");

        when(userRepository.findById(userPk)).thenReturn(Optional.of(mockUser));

        // when
        AdminBanResponse response = adminService.banUser(userPk, request);

        // then
        assertNotNull(response);
        assertEquals(userPk, response.userId());
        verify(mockUser, times(1)).applyBan(request.banType()); // 도메인 로직 호출 검증
        verify(penaltyRepository, times(1)).save(any(Penalty.class)); // 패널티 이력 저장 검증
    }

    @Test
    @DisplayName("유저 밴 실패 - 대상 유저가 관리자(ADMIN)인 경우 예외 발생")
    void banUser_Fail_CannotBanAdmin() {
        // given
        String adminPk = "admin123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ETC, "관리자 밴 시도");

        User mockAdmin = mock(User.class);
        when(mockAdmin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(adminPk)).thenReturn(Optional.of(mockAdmin));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.banUser(adminPk, request));
        assertEquals(ErrorCode.CANNOT_BAN_ADMIN, exception.getErrorCode());
        verify(penaltyRepository, never()).save(any());
    }

    @Test
    @DisplayName("유저 밴 실패 - 이미 정지되었거나 탈퇴한(비활성) 유저인 경우 예외 발생")
    void banUser_Fail_AlreadyBannedUser() {
        // given
        String userPk = "user123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.SPAM, null);

        User mockUser = mock(User.class);
        when(mockUser.getRole()).thenReturn(Role.USER);
        when(mockUser.getIsActive()).thenReturn(false); // 이미 비활성화됨
        when(userRepository.findById(userPk)).thenReturn(Optional.of(mockUser));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.banUser(userPk, request));
        assertEquals(ErrorCode.ALREADY_BANNED_USER, exception.getErrorCode());
    }

    @Test
    @DisplayName("유저 밴 실패 - 존재하지 않는 유저 조회 시 예외 발생")
    void banUser_Fail_UserNotFound() {
        // given
        String invalidUserPk = "invalid";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.SPAM, null);

        when(userRepository.findById(invalidUserPk)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.banUser(invalidUserPk, request));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("패널티 이력 조회 성공 - 최신순으로 정렬된 패널티 이력을 반환한다.")
    void getPenalties_Success() {
        // given
        String userPk = "user123";
        User mockUser = mock(User.class);
        when(userRepository.findById(userPk)).thenReturn(Optional.of(mockUser));

        Penalty penalty1 = mock(Penalty.class);
        Penalty penalty2 = mock(Penalty.class);
        // PenaltyResponse 변환 시 필요한 필드 모킹 생략 (실제 Entity/DTO에 맞게 설정)

        when(penaltyRepository.findByUserOrderByBannedAtDesc(mockUser))
                .thenReturn(List.of(penalty1, penalty2));

        // when
        List<PenaltyResponse> responses = adminService.getPenalties(userPk);

        // then
        assertEquals(2, responses.size());
        verify(penaltyRepository, times(1)).findByUserOrderByBannedAtDesc(mockUser);
    }
}