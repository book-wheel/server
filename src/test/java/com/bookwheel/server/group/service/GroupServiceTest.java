package com.bookwheel.server.group.service;

import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.GroupCreateRequest;
import com.bookwheel.server.group.dto.GroupCreateResponse;
import com.bookwheel.server.group.dto.member.GroupJoinRequest;
import com.bookwheel.server.group.dto.search.GroupSearchCondition;
import com.bookwheel.server.group.dto.search.GroupSearchResponse;
import com.bookwheel.server.group.dto.setting.MemberRequestStatus;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.service.RecruitingScheduleAssignmentService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Mock
    private RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository,
                chatRoomRepository,
                memberRepository,
                userRepository,
                passwordEncoder,
                eventPublisher,
                memberPermissionValidator,
                recruitingScheduleAssignmentService,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("삭제된 모임과 같은 이름으로 새 모임을 만들 수 있다")
    void createGroup_AllowsDeletedGroupName() {
        GroupCreateRequest request = groupCreateRequest(LocalDate.now(FIXED_CLOCK).plusDays(1));
        User user = activeUser();
        given(groupRepository.existsNotDeletedByGroupName(request.groupName(), State.DELETED)).willReturn(false);
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(user));
        given(groupRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        GroupCreateResponse response = groupService.createGroup(request, "leader-user-pk");

        assertThat(response.groupId()).isNotBlank();
        then(groupRepository).should().existsNotDeletedByGroupName(request.groupName(), State.DELETED);
        then(groupRepository).should().save(any());
    }

    @Test
    @DisplayName("오늘 시작하는 모임은 생성할 수 없다")
    void createGroup_RejectsTodayStartDate() {
        GroupCreateRequest request = groupCreateRequest(LocalDate.now(FIXED_CLOCK));
        given(groupRepository.existsNotDeletedByGroupName(request.groupName(), State.DELETED)).willReturn(false);

        assertThatThrownBy(() -> groupService.createGroup(request, "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);

        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 목표 인원을 채운 모임에는 가입 신청을 만들지 않는다")
    void joinGroup_RejectsWhenTargetMemberCountReached() {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("목표 인원 모임")
                .groupPublic(true)
                .maxMembers(10)
                .targetMemberCount(4)
                .currentMembers(4)
                .groupState(State.RECRUITING)
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("member-user-pk")).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> groupService.joinGroup(
                groupId,
                new GroupJoinRequest(null, "가입하고 싶습니다"),
                "member-user-pk"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_EXCEEDED);

        then(memberRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("시작일이 지난 모집 모임에는 가입 신청을 만들지 않는다")
    void joinGroup_RejectsWhenStartDatePassed() {
        String groupId = "expired-recruiting-group";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("시작일 경과 모임")
                .groupPublic(true)
                .maxMembers(5)
                .startDate(LocalDate.now(FIXED_CLOCK).minusDays(1))
                .groupState(State.RECRUITING)
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("member-user-pk")).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> groupService.joinGroup(
                groupId,
                new GroupJoinRequest(null, "가입하고 싶습니다"),
                "member-user-pk"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_PERIOD_EXPIRED);

        then(memberRepository).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("시작 당일인 모집 모임에는 가입 신청할 수 있다")
    void joinGroup_AllowsOnStartDate() {
        String groupId = "today-recruiting-group";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("오늘 시작 모임")
                .groupPublic(true)
                .maxMembers(5)
                .startDate(LocalDate.now(FIXED_CLOCK))
                .groupState(State.RECRUITING)
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("member-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        groupService.joinGroup(
                groupId,
                new GroupJoinRequest(null, "가입하고 싶습니다"),
                "member-user-pk"
        );

        then(memberRepository).should().save(any());
    }

    @Test
    @DisplayName("시작일이 지난 모집 모임의 대기 중 가입 요청은 승인할 수 없다")
    void updateMemberRequestStatus_RejectsApprovalWhenStartDatePassed() {
        String groupId = "expired-recruiting-group";
        String memberId = "pending-member";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("시작일 경과 모임")
                .groupPublic(true)
                .maxMembers(5)
                .startDate(LocalDate.now(FIXED_CLOCK).minusDays(1))
                .groupState(State.RECRUITING)
                .build();
        Member pendingMember = Member.builder()
                .memberId(memberId)
                .group(group)
                .user(activeUser())
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.PENDING)
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId))
                .willReturn(Optional.of(pendingMember));

        assertThatThrownBy(() -> groupService.updateMemberRequestStatus(
                groupId,
                memberId,
                "leader-user-pk",
                MemberRequestStatus.APPROVED
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_PERIOD_EXPIRED);

        then(memberRepository).should(never()).countByGroup_GroupIdAndMemberStatus(any(), any());
        then(recruitingScheduleAssignmentService).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("그룹 목록 D-day는 UTC 날짜가 아닌 KST 날짜를 기준으로 계산한다")
    void getGroups_CalculatesDdayWithKstClock() {
        Clock kstBoundaryClock = Clock.fixed(
                Instant.parse("2026-07-15T15:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        groupService = new GroupService(
                groupRepository,
                chatRoomRepository,
                memberRepository,
                userRepository,
                passwordEncoder,
                eventPublisher,
                memberPermissionValidator,
                recruitingScheduleAssignmentService,
                kstBoundaryClock
        );
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("KST 모임")
                .groupState(State.RECRUITING)
                .startDate(LocalDate.of(2026, 7, 17))
                .maxMembers(5)
                .build();
        Page<Group> groups = new PageImpl<>(List.of(group));
        given(groupRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Group>>any(),
                any(Pageable.class)
        )).willReturn(groups);

        Page<GroupSearchResponse> response = groupService.getGroups(
                new GroupSearchCondition(null, null, null, null),
                Pageable.unpaged(),
                null
        );

        assertThat(response.getContent()).singleElement()
                .extracting(GroupSearchResponse::dday)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("시작일을 놓친 내 모임은 D-day 대신 일정 재설정 필요 상태를 반환한다")
    void getMyGroups_ReturnsRescheduleRequiredWithoutDday() {
        Group group = Group.builder()
                .groupId("expired-group")
                .groupName("시작일 경과 모임")
                .groupState(State.RECRUITING)
                .startDate(LocalDate.now(FIXED_CLOCK).minusDays(2))
                .maxMembers(5)
                .build();
        given(memberRepository.findGroupsByUserPKAndMemberStatus(
                "leader-user-pk",
                MemberStatus.ACTIVE
        )).willReturn(List.of(group));

        List<GroupSearchResponse> response = groupService.getMyGroups("leader-user-pk");

        assertThat(response).singleElement().satisfies(expiredGroup -> {
            assertThat(expiredGroup.status()).isEqualTo("reschedule_required");
            assertThat(expiredGroup.groupStateLabel()).isEqualTo("일정 재설정 필요");
            assertThat(expiredGroup.dday()).isNull();
        });
    }

    private GroupCreateRequest groupCreateRequest(LocalDate startDate) {
        return new GroupCreateRequest(
                "삭제된 모임 이름",
                "한줄소개",
                "규칙",
                true,
                null,
                false,
                null,
                7,
                startDate,
                5
        );
    }

    private User activeUser() {
        return User.builder()
                .loginId("leader-login")
                .password("password")
                .nickname("리더")
                .mail("leader@example.com")
                .isActive(true)
                .build();
    }
}
