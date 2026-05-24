package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.dto.member.*;
import com.bookwheel.server.group.dto.search.GroupSearchCondition;
import com.bookwheel.server.group.dto.search.GroupSearchResponse;
import com.bookwheel.server.group.dto.setting.*;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

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
    @DisplayName("비공개 그룹 생성 성공 - 비밀번호가 암호화되고 리더로 등록된다.")
    void createGroup_Private_Success() {
        // given
        String userPK = "user1";
        GroupCreateRequest request = new GroupCreateRequest(
                "이름",
                "설명",
                "규칙",
                false,
                "1234",
                true,
                Region.SEOUL,
                7,
                LocalDate.now().plusDays(1),
                10
        );

        User mockUser = mock(User.class);
        when(mockUser.getIsActive()).thenReturn(true);
        when(userRepository.findById(userPK)).thenReturn(Optional.of(mockUser));
        when(groupRepository.existsByGroupName(request.groupName())).thenReturn(false);
        when(passwordEncoder.encode("1234")).thenReturn("encoded_1234");

        Group mockGroup = mock(Group.class);
        when(mockGroup.getGroupId()).thenReturn("group-uuid");
        when(groupRepository.save(any(Group.class))).thenReturn(mockGroup);

        // when
        GroupCreateResponse response = groupService.createGroup(request, userPK);

        // then
        assertNotNull(response);
        verify(passwordEncoder, times(1)).encode("1234");
        verify(memberRepository, times(1)).save(any(Member.class)); // 리더 등록 검증
    }

    @Test
    @DisplayName("그룹 생성 실패 - 중복된 그룹명")
    void createGroup_Fail_DuplicateName() {
        // given
        GroupCreateRequest request = new GroupCreateRequest(
                "중복이름",
                "설명",
                "규칙",
                true,
                null,
                false,
                null,
                7,
                LocalDate.now().plusDays(1),
                10
        );
        when(groupRepository.existsByGroupName("중복이름")).thenReturn(true);

        // when & then
        BusinessException e = assertThrows(BusinessException.class, () -> groupService.createGroup(request, "user1"));
        assertEquals(ErrorCode.DUPLICATE_GROUP_NAME, e.getErrorCode());
    }

    @Test
    @DisplayName("그룹 생성 실패 - 오프라인 그룹인데 지역 정보가 없음")
    void createGroup_Fail_OfflineWithoutRegion() {
        // given
        GroupCreateRequest request = new GroupCreateRequest(
                "이름",
                "설명",
                "규칙",
                true,
                null,
                true,
                null,
                7,
                LocalDate.now().plusDays(1),
                10
        );
        when(groupRepository.existsByGroupName(anyString())).thenReturn(false);

        // when & then
        BusinessException e = assertThrows(BusinessException.class, () -> groupService.createGroup(request, "user1"));
        assertEquals(ErrorCode.GROUP_REGION_REQUIRED, e.getErrorCode());
    }

    @Test
    @DisplayName("그룹 가입 신청 성공 - Pending 상태로 저장된다.")
    void joinGroup_Success() {
        // given
        String groupId = "group1";
        String userPK = "user1";
        GroupJoinRequest request = new GroupJoinRequest("가입인사", null); // 공개 그룹

        Group mockGroup = mock(Group.class);
        when(mockGroup.isGroupPublic()).thenReturn(true);
        when(mockGroup.getCurrentMembers()).thenReturn(1);
        when(mockGroup.getMaxMembers()).thenReturn(10);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));

        User mockUser = mock(User.class);
        when(mockUser.getIsActive()).thenReturn(true);
        when(userRepository.findById(userPK)).thenReturn(Optional.of(mockUser));

        when(memberRepository.existsByGroup_GroupIdAndUser_Id(groupId, userPK)).thenReturn(false);

        Member mockMember = mock(Member.class);
        when(mockMember.getMemberId()).thenReturn("member-uuid");
        when(mockMember.getMemberStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.save(any(Member.class))).thenReturn(mockMember);

        // when
        GroupJoinResponse response = groupService.joinGroup(groupId, request, userPK);

        // then
        assertNotNull(response);
        assertEquals(MemberStatus.PENDING, response.status());
    }

    @Test
    @DisplayName("그룹 목록 조회 - 현재 사용자 기준 bottomButtonType을 페이지당 한 번의 멤버십 조회로 매핑한다.")
    void getGroups_IncludesBottomButtonTypes() {
        // given
        String userPK = "user1";
        Group leaderGroup = mockGroup("leader-group");
        Group joinedGroup = mockGroup("joined-group");
        Group pendingGroup = mockGroup("pending-group");
        Group joinGroup = mockGroup("join-group");
        List<Group> groups = List.of(leaderGroup, joinedGroup, pendingGroup, joinGroup);
        List<String> groupIds = List.of("leader-group", "joined-group", "pending-group", "join-group");
        PageRequest pageable = PageRequest.of(0, 4);

        when(groupRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Group>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(groups, pageable, groups.size()));
        when(memberRepository.findMembershipSummariesByUserIdAndGroupIds(eq(userPK), eq(groupIds)))
                .thenReturn(List.of(
                        new TestGroupMembershipSummary("leader-group", MemberRole.LEADER, MemberStatus.ACTIVE),
                        new TestGroupMembershipSummary("joined-group", MemberRole.MEMBER, MemberStatus.ACTIVE),
                        new TestGroupMembershipSummary("pending-group", MemberRole.MEMBER, MemberStatus.PENDING)
                ));

        // when
        Page<GroupSearchResponse> response = groupService.getGroups(
                new GroupSearchCondition(null, null, null, null),
                pageable,
                userPK
        );

        // then
        assertEquals(
                List.of(
                        GroupDetailButtonType.LEADER_SETTING,
                        GroupDetailButtonType.JOINED,
                        GroupDetailButtonType.JOINED,
                        GroupDetailButtonType.JOIN
                ),
                response.getContent().stream()
                        .map(GroupSearchResponse::bottomButtonType)
                        .toList()
        );
        verify(memberRepository, times(1)).findMembershipSummariesByUserIdAndGroupIds(eq(userPK), eq(groupIds));
        verify(memberRepository, never()).findByGroup_GroupIdAndUser_Id(anyString(), eq(userPK));
    }

    @Test
    @DisplayName("그룹 목록 조회 - 비로그인 사용자는 멤버십 조회 없이 JOIN을 반환한다.")
    void getGroups_GuestReturnsJoinWithoutMembershipLookup() {
        // given
        Group firstGroup = mockGroup("first-group");
        Group secondGroup = mockGroup("second-group");
        List<Group> groups = List.of(firstGroup, secondGroup);
        PageRequest pageable = PageRequest.of(0, 2);

        when(groupRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Group>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(groups, pageable, groups.size()));

        // when
        Page<GroupSearchResponse> response = groupService.getGroups(
                new GroupSearchCondition(null, null, null, null),
                pageable,
                null
        );

        // then
        assertEquals(
                List.of(GroupDetailButtonType.JOIN, GroupDetailButtonType.JOIN),
                response.getContent().stream()
                        .map(GroupSearchResponse::bottomButtonType)
                        .toList()
        );
        verify(memberRepository, never()).findMembershipSummariesByUserIdAndGroupIds(anyString(), anyList());
    }

    @Test
    @DisplayName("그룹 상세 조회 - ACTIVE 리더는 LEADER_SETTING을 반환한다.")
    void getGroup_ReturnsLeaderSettingForActiveLeader() {
        // given
        String groupId = "group1";
        String userPK = "user1";
        Group group = mockGroup(groupId);
        Member member = mock(Member.class);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)).thenReturn(Optional.of(member));
        when(member.getMemberRole()).thenReturn(MemberRole.LEADER);
        when(member.getMemberStatus()).thenReturn(MemberStatus.ACTIVE);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, userPK);

        // then
        assertEquals(GroupDetailButtonType.LEADER_SETTING, response.bottomButtonType());
    }

    @Test
    @DisplayName("그룹 상세 조회 - 가입 신청 중인 사용자는 JOINED를 반환한다.")
    void getGroup_ReturnsPendingForPendingMember() {
        // given
        String groupId = "group1";
        String userPK = "user1";
        Group group = mockGroup(groupId);
        Member member = mock(Member.class);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)).thenReturn(Optional.of(member));
        when(member.getMemberRole()).thenReturn(MemberRole.MEMBER);
        when(member.getMemberStatus()).thenReturn(MemberStatus.PENDING);

        // when
        GroupDetailResponse response = groupService.getGroup(groupId, userPK);

        // then
        assertEquals(GroupDetailButtonType.JOINED, response.bottomButtonType());
    }

    @Test
    @DisplayName("가입 요청 처리 성공 - 리더가 요청을 승인하면 멤버 approve를 호출한다.")
    void updateMemberRequestStatus_Approve_Success() {
        // given
        String groupId = "group1";
        String memberId = "member1";
        String leaderId = "leader1";

        Group mockGroup = mock(Group.class);
        when(mockGroup.getCurrentMembers()).thenReturn(5);
        when(mockGroup.getMaxMembers()).thenReturn(10);
        when(groupRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(mockGroup)); // APPROVED인 경우 Lock 적용 메서드 호출

        doNothing().when(memberPermissionValidator).validateLeader(groupId, leaderId);

        // 대상 멤버 대기 상태 모킹
        Member targetMember = mock(Member.class);
        when(targetMember.getMemberStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId)).thenReturn(Optional.of(targetMember));

        // when
        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(groupId, memberId, leaderId, MemberRequestStatus.APPROVED);

        // then
        assertEquals(MemberRequestStatus.APPROVED, response.status());
        verify(targetMember, times(1)).approve();
    }

    @Test
    @DisplayName("가입 요청 처리 실패 - 리더 권한이 없는 유저가 처리 시도 시 예외 발생")
    void updateMemberRequestStatus_Fail_NotLeader() {
        // given
        String groupId = "group1";
        String memberId = "member1";
        String normalUserPK = "user2";

        Group mockGroup = mock(Group.class);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));
        doThrow(new BusinessException(ErrorCode.GROUP_LEADER_ONLY))
                .when(memberPermissionValidator)
                .validateLeader(groupId, normalUserPK);

        // when & then
        BusinessException e = assertThrows(BusinessException.class, () ->
                groupService.updateMemberRequestStatus(groupId, memberId, normalUserPK, MemberRequestStatus.REJECTED));

        assertEquals(ErrorCode.GROUP_LEADER_ONLY, e.getErrorCode());
    }

    private Group mockGroup(String groupId) {
        Group group = mock(Group.class);
        when(group.getGroupId()).thenReturn(groupId);
        when(group.getGroupState()).thenReturn(State.RECRUITING);
        when(group.getStartDate()).thenReturn(LocalDate.now().plusDays(1));
        when(group.getMaxMembers()).thenReturn(10);
        return group;
    }

    private record TestGroupMembershipSummary(
            String groupId,
            MemberRole memberRole,
            MemberStatus memberStatus
    ) implements MemberRepository.GroupMembershipSummary {
        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public MemberRole getMemberRole() {
            return memberRole;
        }

        @Override
        public MemberStatus getMemberStatus() {
            return memberStatus;
        }
    }
}
