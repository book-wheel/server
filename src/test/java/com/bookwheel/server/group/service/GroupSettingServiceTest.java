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
import com.bookwheel.server.group.enums.ScheduleReconfigurationStatus;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.service.NotificationService;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.schedule.service.RecruitingScheduleAssignmentService;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import com.bookwheel.server.user.entity.User;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;

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
        given(groupRepository.existsNotDeletedByGroupNameAndGroupIdNot(
                request.groupName(), groupId, State.DELETED
        )).willReturn(false);
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
        given(groupRepository.existsNotDeletedByGroupNameAndGroupIdNot(
                request.groupName(), "group-1", State.DELETED
        )).willReturn(false);

        for (State state : List.of(State.RECRUITING, State.IN_PROGRESS, State.COMPLETE)) {
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
    @DisplayName("삭제된 모임은 정보를 수정할 수 없다")
    void updateGroup_RejectsDeletedGroup() {
        Group group = recruitingGroup("group-1");
        group.markDeleted();
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
        given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.of(group));

        assertThatThrownBy(() -> groupSettingService.updateGroup("group-1", "leader-user-pk", request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_DELETED);

        then(memberPermissionValidator).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제된 모임과 같은 이름으로 모임 정보를 수정할 수 있다")
    void updateGroup_AllowsDeletedGroupName() {
        String groupId = "group-1";
        Group group = recruitingGroup(groupId);
        GroupUpdateRequest request = new GroupUpdateRequest(
                "삭제된 모임 이름",
                "수정된 한줄소개",
                "수정된 규칙",
                true,
                null,
                false,
                null,
                5
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(groupRepository.existsNotDeletedByGroupNameAndGroupIdNot(
                request.groupName(), groupId, State.DELETED
        )).willReturn(false);
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE)).willReturn(1L);

        GroupDetailResponse response = groupSettingService.updateGroup(groupId, "leader-user-pk", request);

        assertThat(response.groupName()).isEqualTo("삭제된 모임 이름");
        then(groupRepository).should().existsNotDeletedByGroupNameAndGroupIdNot(
                request.groupName(), groupId, State.DELETED
        );
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

    @Test
    @DisplayName("진행 중 멤버 강퇴 시 미래 라운드는 보존하고 리더 재확인 상태로 전환한다")
    void kickMember_PreservesFutureRoundsAndRequiresReadOrderConfirmation() {
        String groupId = "group-1";
        LocalDate today = LocalDate.of(2026, 7, 30);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("진행 중 모임")
                .groupState(State.IN_PROGRESS)
                .startDate(today.minusDays(6))
                .groupRoundCount(2)
                .maxMembers(4)
                .build();
        User leaderUser = activeUser("리더");
        User targetUser = activeUser("대상");
        Member leader = activeMember("member-1", group, leaderUser, MemberRole.LEADER);
        Member target = activeMember("member-2", group, targetUser, MemberRole.MEMBER);
        Round currentRound = round("round-1", group, 1, today.minusDays(6), today);
        Round futureExecutableRound = round("round-2", group, 2, today.plusDays(1), today.plusDays(7));
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(leader, target));
        given(clock.instant()).willReturn(Instant.parse("2026-07-30T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(currentRound, futureExecutableRound));

        groupSettingService.kickMember(groupId, targetUser.getId(), leaderUser.getId());

        assertThat(target.getMemberStatus()).isEqualTo(MemberStatus.BANNED);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        assertThat(group.getScheduleReconfigurationStatus())
                .isEqualTo(ScheduleReconfigurationStatus.READ_ORDER_CONFIRMATION_REQUIRED);
        then(wheelReassignmentService).should().deleteReplaceableFutureAssignments(List.of(futureExecutableRound));
        then(roundRepository).should(never()).deleteAllByIdInBatch(List.of(futureExecutableRound.getRoundId()));
        then(roundRepository).should(never()).findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
    }

    @Test
    @DisplayName("진행 중 멤버 탈퇴 시 미래 라운드는 보존하고 리더 재확인 상태로 전환한다")
    void exitMember_PreservesFutureRoundsAndRequiresReadOrderConfirmation() {
        String groupId = "group-1";
        LocalDate today = LocalDate.of(2026, 7, 30);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("진행 중 모임")
                .groupState(State.IN_PROGRESS)
                .startDate(today.minusDays(6))
                .groupRoundCount(2)
                .maxMembers(4)
                .build();
        User exitingUser = activeUser("탈퇴자");
        Member exitingMember = activeMember("member-2", group, exitingUser, MemberRole.MEMBER);
        Round currentRound = round("round-1", group, 1, today.minusDays(6), today);
        Round futureRound = round("round-2", group, 2, today.plusDays(1), today.plusDays(7));

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(exitingMember));
        given(clock.instant()).willReturn(Instant.parse("2026-07-30T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(roundRepository.findCurrentRound(groupId, today, State.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(currentRound, futureRound));

        groupSettingService.exitMember(groupId, exitingUser.getId());

        assertThat(exitingMember.getMemberStatus()).isEqualTo(MemberStatus.EXITED);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        assertThat(group.getScheduleReconfigurationStatus())
                .isEqualTo(ScheduleReconfigurationStatus.READ_ORDER_CONFIRMATION_REQUIRED);
        then(wheelReassignmentService).should().deleteReplaceableFutureAssignments(List.of(futureRound));
        then(roundRepository).should(never()).deleteAllByIdInBatch(List.of(futureRound.getRoundId()));
    }

    @Test
    @DisplayName("미래 라운드가 없는 진행 중 모임에서 탈퇴해도 재확정 상태로 전환하지 않는다")
    void exitMember_DoesNotRequireReconfirmationWithoutFutureRound() {
        String groupId = "group-1";
        LocalDate today = LocalDate.of(2026, 7, 30);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("마지막 라운드 종료 모임")
                .groupState(State.IN_PROGRESS)
                .startDate(today.minusDays(7))
                .groupRoundCount(1)
                .maxMembers(2)
                .build();
        User exitingUser = activeUser("탈퇴자");
        Member exitingMember = activeMember("member-2", group, exitingUser, MemberRole.MEMBER);
        Round lastRound = round("round-1", group, 1, today.minusDays(7), today.minusDays(1));

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(exitingMember));
        given(clock.instant()).willReturn(Instant.parse("2026-07-30T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(roundRepository.findCurrentRound(groupId, today, State.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(lastRound));

        groupSettingService.exitMember(groupId, exitingUser.getId());

        assertThat(exitingMember.getMemberStatus()).isEqualTo(MemberStatus.EXITED);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        assertThat(group.getScheduleReconfigurationStatus()).isEqualTo(ScheduleReconfigurationStatus.NONE);
        then(wheelReassignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미래 일정 재확정 중 추가 강퇴가 발생하면 읽기 순서 확인 단계로 돌아간다")
    void kickMember_ReturnsToReadOrderConfirmationWhenReconfigurationIsAlreadyPending() {
        String groupId = "group-1";
        LocalDate today = LocalDate.of(2026, 7, 30);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("재확정 중 모임")
                .groupState(State.IN_PROGRESS)
                .scheduleReconfigurationStatus(ScheduleReconfigurationStatus.FUTURE_SCHEDULE_CONFIRMATION_REQUIRED)
                .startDate(today.minusDays(6))
                .groupRoundCount(1)
                .maxMembers(3)
                .build();
        User leaderUser = activeUser("리더");
        User targetUser = activeUser("대상");
        Member leader = activeMember("member-1", group, leaderUser, MemberRole.LEADER);
        Member target = activeMember("member-2", group, targetUser, MemberRole.MEMBER);
        Round currentRound = round("round-1", group, 1, today.minusDays(6), today);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(leader, target));
        given(clock.instant()).willReturn(Instant.parse("2026-07-30T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(currentRound));

        groupSettingService.kickMember(groupId, targetUser.getId(), leaderUser.getId());

        assertThat(target.getMemberStatus()).isEqualTo(MemberStatus.BANNED);
        assertThat(group.getScheduleReconfigurationStatus())
                .isEqualTo(ScheduleReconfigurationStatus.READ_ORDER_CONFIRMATION_REQUIRED);
        then(wheelReassignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("진행 중인 모임은 삭제할 수 없다")
    void deleteGroup_RejectsInProgressWithoutSideEffects() {
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("진행 중 모임")
                .groupState(State.IN_PROGRESS)
                .build();
        given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.of(group));

        assertThatThrownBy(() -> groupSettingService.deleteGroup("group-1", "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_DELETE_IN_PROGRESS_NOT_ALLOWED);

        then(notificationService).shouldHaveNoInteractions();
        then(groupRepository).should(never()).delete(group);
    }

    @Test
    @DisplayName("모임 이미지 삭제는 DB 커밋 이후에 실행한다")
    void deleteGroup_DeletesImagesAfterCommit() {
        String groupId = "group-1";
        Group group = recruitingGroup(groupId);
        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomId("chat-room-1")
                .group(group)
                .build();
        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .imageKey("chat/group-1/image.png")
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(chatRoomRepository.findByGroup_GroupId(groupId)).willReturn(Optional.of(chatRoom));
        given(chatMessageRepository.findByChatRoom(chatRoom)).willReturn(List.of(message));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            groupSettingService.deleteGroup(groupId, "leader-user-pk");

            then(s3Service).shouldHaveNoInteractions();
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
            then(s3Service).should().deleteObject("chat/group-1/image.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("모집 중인 모임 삭제 시 운영 데이터를 정리하고 모임은 DELETED로 남긴다")
    void deleteGroup_DeletesOperationalDataAndPreservesGroup() {
        String groupId = "group-1";
        Group group = recruitingGroup(groupId);
        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomId("chat-room-1")
                .group(group)
                .build();
        Round round = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(LocalDate.of(2026, 7, 20))
                .endDate(LocalDate.of(2026, 7, 26))
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(chatRoomRepository.findByGroup_GroupId(groupId)).willReturn(Optional.of(chatRoom));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of(round));
        given(wheelStateRepository.findByRoundIdIn(List.of(round.getRoundId()))).willReturn(List.of());

        groupSettingService.deleteGroup(groupId, "leader-user-pk");

        InOrder deletionOrder = inOrder(
                chatRoomReadStateRepository,
                chatMessageRepository,
                chatRoomRepository,
                wheelStateRepository,
                roundRepository,
                ownBookRepository,
                memberRepository,
                entityManager
        );
        deletionOrder.verify(chatRoomReadStateRepository).deleteAllByChatRoom(chatRoom);
        deletionOrder.verify(chatMessageRepository).deleteAllByChatRoom(chatRoom);
        deletionOrder.verify(chatRoomRepository).delete(chatRoom);
        deletionOrder.verify(chatRoomRepository).flush();
        deletionOrder.verify(wheelStateRepository).deleteAll(List.of());
        deletionOrder.verify(wheelStateRepository).flush();
        deletionOrder.verify(roundRepository).deleteAll(List.of(round));
        deletionOrder.verify(roundRepository).flush();
        deletionOrder.verify(ownBookRepository).deleteAllByGroupId(groupId);
        deletionOrder.verify(memberRepository).deleteAllByGroupId(groupId);
        deletionOrder.verify(entityManager).flush();
        deletionOrder.verify(entityManager).clear();
        assertThat(group.getGroupState()).isEqualTo(State.DELETED);
        then(groupRepository).should(never()).delete(group);
    }

    @Test
    @DisplayName("모임 삭제 시 게시물과 커뮤니티 데이터는 보존한다")
    void deleteGroup_PreservesCommunityData() {
        String groupId = "group-1";
        Group group = recruitingGroup(groupId);
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());

        groupSettingService.deleteGroup(groupId, "leader-user-pk");

        assertThat(group.getGroupState()).isEqualTo(State.DELETED);
        then(postRepository).shouldHaveNoInteractions();
        then(postLikeRepository).shouldHaveNoInteractions();
        then(postCommentRepository).shouldHaveNoInteractions();
        then(postReportRepository).shouldHaveNoInteractions();
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

    private User activeUser(String nickname) {
        return User.builder()
                .loginId(nickname + "-login")
                .password("password")
                .nickname(nickname)
                .mail(nickname + "@example.com")
                .isActive(true)
                .build();
    }

    private Member activeMember(
            String memberId,
            Group group,
            User user,
            MemberRole memberRole
    ) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user)
                .memberRole(memberRole)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }

    private Round round(
            String roundId,
            Group group,
            int roundNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return Round.builder()
                .roundId(roundId)
                .group(group)
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
