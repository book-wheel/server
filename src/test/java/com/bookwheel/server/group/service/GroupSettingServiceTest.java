package com.bookwheel.server.group.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.chat.entity.ChatMessage;
import com.bookwheel.server.chat.entity.ChatRoom;
import com.bookwheel.server.chat.repository.ChatMessageRepository;
import com.bookwheel.server.chat.repository.ChatRoomReadStateRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostReportRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.group.dto.GroupDetailButtonType;
import com.bookwheel.server.group.dto.GroupDetailResponse;
import com.bookwheel.server.group.dto.setting.GroupUpdateRequest;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.service.NotificationService;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupSettingServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomReadStateRepository chatRoomReadStateRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostReportRepository postReportRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private S3Service s3Service;

    @Mock
    private WheelReassignmentService wheelReassignmentService;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Clock clock;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private GroupSettingService groupSettingService;

    @Test
    @DisplayName("리더가 모집 중인 모임 정보를 수정하면 변경된 상세 정보를 반환한다")
    void updateGroup_UpdatesRecruitingGroup() {
        String groupId = "group-1";
        String leaderUserPK = "leader-user-pk";
        Group group = recruitingGroup(groupId);
        GroupUpdateRequest request = new GroupUpdateRequest(
                "수정된 모임",
                "수정된 한줄소개",
                "수정된 규칙",
                false,
                "new-password",
                true,
                Region.SEOUL,
                5
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE)).willReturn(2L);
        given(groupRepository.existsByGroupNameAndGroupIdNot(request.groupName(), groupId)).willReturn(false);
        given(passwordEncoder.encode(request.groupPassword())).willReturn("encoded-password");

        GroupDetailResponse response = groupSettingService.updateGroup(groupId, leaderUserPK, request);

        assertThat(response.groupName()).isEqualTo(request.groupName());
        assertThat(response.bottomButtonType()).isEqualTo(GroupDetailButtonType.LEADER_SETTING);
        assertThat(group.getGroupPassword()).isEqualTo("encoded-password");
        assertThat(group.getGroupRegion()).isEqualTo(Region.SEOUL);
        then(memberPermissionValidator).should().validateLeader(groupId, leaderUserPK);
        then(passwordEncoder).should().encode(request.groupPassword());
    }

    @Test
    @DisplayName("현재 참여 인원보다 적은 최대 인원으로는 모임 정보를 수정할 수 없다")
    void updateGroup_RejectsMaximumBelowCurrentMemberCount() {
        String groupId = "group-1";
        Group group = recruitingGroup(groupId);
        GroupUpdateRequest request = new GroupUpdateRequest(
                group.getGroupName(),
                group.getGroupComment(),
                group.getGroupRule(),
                true,
                null,
                false,
                null,
                2
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE)).willReturn(3L);

        assertThatThrownBy(() -> groupSettingService.updateGroup(groupId, "leader-user-pk", request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MAX_MEMBERS_BELOW_CURRENT_MEMBERS);
    }

    @Test
    @DisplayName("모집 중·진행 중·완료된 모임 모두 일반 정보를 수정할 수 있다")
    void updateGroup_AllowsEveryLifecycleState() {
        GroupUpdateRequest request = new GroupUpdateRequest(
                "새 모임 이름",
                "새 한줄소개",
                "새 규칙",
                true,
                null,
                false,
                null,
                5
        );
        given(memberRepository.countByGroup_GroupIdAndMemberStatus("group-1", MemberStatus.ACTIVE)).willReturn(2L);
        given(groupRepository.existsByGroupNameAndGroupIdNot(request.groupName(), "group-1")).willReturn(false);

        for (State state : State.values()) {
            Group group = Group.builder()
                    .groupId("group-1")
                    .groupName("기존 모임")
                    .groupComment("기존 한줄소개")
                    .groupRule("기존 규칙")
                    .groupPublic(true)
                    .groupOffline(false)
                    .readingPeriod(7)
                    .maxMembers(5)
                    .groupState(state)
                    .build();
            given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.of(group));

            GroupDetailResponse response = groupSettingService.updateGroup("group-1", "leader-user-pk", request);

            assertThat(response.groupState()).isEqualTo(state);
            assertThat(group.getGroupName()).isEqualTo("새 모임 이름");
        }
    }

    @Test
    @DisplayName("기존 비공개 모임도 수정 요청에 비밀번호가 없으면 거절한다")
    void updateGroup_RequiresPasswordForEveryPrivateRequest() {
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("기존 모임")
                .groupComment("기존 한줄소개")
                .groupRule("기존 규칙")
                .groupPublic(false)
                .groupPassword("old-encoded")
                .groupOffline(false)
                .maxMembers(5)
                .groupState(State.IN_PROGRESS)
                .build();
        GroupUpdateRequest request = new GroupUpdateRequest(
                "기존 모임",
                "기존 한줄소개",
                "기존 규칙",
                false,
                null,
                false,
                null,
                5
        );
        given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.of(group));

        assertThatThrownBy(() -> groupSettingService.updateGroup("group-1", "leader-user-pk", request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PASSWORD_REQUIRED);
    }

    private Group recruitingGroup(String groupId) {
        return Group.builder()
                .groupId(groupId)
                .groupName("기존 모임")
                .groupComment("기존 한줄소개")
                .groupRule("기존 규칙")
                .groupPublic(true)
                .groupOffline(false)
                .readingPeriod(7)
                .startDate(LocalDate.of(2026, 7, 20))
                .maxMembers(5)
                .groupState(State.RECRUITING)
                .build();
    }
}
