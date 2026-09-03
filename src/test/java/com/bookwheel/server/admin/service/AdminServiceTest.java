package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.dto.AdminBanRequest;
import com.bookwheel.server.admin.dto.AdminBanResponse;
import com.bookwheel.server.admin.dto.AdminPostDeleteRequest;
import com.bookwheel.server.admin.dto.BanReason;
import com.bookwheel.server.admin.dto.PenaltyResponse;
import com.bookwheel.server.admin.dto.PostDeletionReason;
import com.bookwheel.server.admin.entity.Penalty;
import com.bookwheel.server.admin.repository.PenaltyRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.service.PostDeletionService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostDeletionService postDeletionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 제재 시각이 실행 환경 시간대에 흔들리지 않는지 확인하기 위해 KST로 고정한다.
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-09-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("이유: " + cause.getMessage());
        }
    };

    @Test
    @DisplayName("유저 밴 성공 - 정상적으로 유저를 제재하고 패널티 이력을 저장한다.")
    void banUser_Success() {
        // given
        String userPK = "user123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ETC, "욕설/비방");
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userPK);
        when(mockUser.getIsActive()).thenReturn(true); // 활성 상태
        when(mockUser.getNickname()).thenReturn("테스트유저");

        when(userRepository.findById(userPK)).thenReturn(Optional.of(mockUser));

        // when
        AdminBanResponse response = adminService.banUser(userPK, request);

        // then
        assertNotNull(response);
        assertEquals(userPK, response.userPK());
        verify(mockUser, times(1)).applyBan(request.banType(), LocalDateTime.of(2026, 9, 3, 12, 0, 0)); // 도메인 로직 호출 검증
        verify(penaltyRepository, times(1)).save(any(Penalty.class)); // 패널티 이력 저장 검증
    }

    @Test
    @DisplayName("유저 밴 실패 - 이미 정지되었거나 탈퇴한(비활성) 유저인 경우 예외 발생")
    void banUser_Fail_AlreadyBannedUser() {
        // given
        String userPK = "user123";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ETC, "스팸/도배");
        User mockUser = mock(User.class);
        when(mockUser.getIsActive()).thenReturn(false); // 이미 비활성화됨
        when(userRepository.findById(userPK)).thenReturn(Optional.of(mockUser));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.banUser(userPK, request));
        assertEquals(ErrorCode.ALREADY_BANNED_USER, exception.getErrorCode());
    }

    @Test
    @DisplayName("유저 밴 실패 - 존재하지 않는 유저 조회 시 예외 발생")
    void banUser_Fail_UserNotFound() {
        // given
        String invaliduserPK = "invalid";
        AdminBanRequest request = new AdminBanRequest("SUSPEND", BanReason.ETC, "스팸/도배");
        when(userRepository.findById(invaliduserPK)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminService.banUser(invaliduserPK, request));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("패널티 이력 조회 성공 - 최신순으로 정렬된 패널티 이력을 반환한다.")
    void getPenalties_Success() {
        // given
        String userPK = "user123";
        User mockUser = mock(User.class);
        when(userRepository.findById(userPK)).thenReturn(Optional.of(mockUser));

        Penalty penalty1 = mock(Penalty.class);
        Penalty penalty2 = mock(Penalty.class);

        when(penaltyRepository.findByUserOrderByBannedAtDesc(mockUser))
                .thenReturn(List.of(penalty1, penalty2));

        // when
        List<PenaltyResponse> responses = adminService.getPenalties(userPK);

        // then
        assertEquals(2, responses.size());
        verify(penaltyRepository, times(1)).findByUserOrderByBannedAtDesc(mockUser);
    }

    @Test
    @DisplayName("게시물 강제 삭제는 공통 게시물 삭제 서비스를 사용한다")
    void deletePost_UsesPostDeletionService() {
        Long postId = 1L;
        Post post = mock(Post.class);
        AdminPostDeleteRequest request = new AdminPostDeleteRequest(PostDeletionReason.OTHER);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(post.getPostId()).thenReturn(postId);

        adminService.deletePost(postId, request);

        verify(postDeletionService, times(1)).delete(post);
    }
}
