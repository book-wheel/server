package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("비공개 그룹 생성 성공 - 비밀번호가 암호화되고 리더로 등록된다.")
    void createGroup_Private_Success() {
        // given
        String userId = "user1";
        GroupCreateRequest request = new GroupCreateRequest(
                "테스트 그룹", "설명", 10, false, "1234", true, Region.SEOUL
        );

        User mockUser = mock(User.class);
        when(mockUser.getIsActive()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(groupRepository.existsByGroupName(request.groupName())).thenReturn(false);
        when(passwordEncoder.encode("1234")).thenReturn("encoded_1234");

        Group mockGroup = mock(Group.class);
        when(mockGroup.getGroupId()).thenReturn("group-uuid");
        when(mockGroup.getGroupPassword()).thenReturn("1234"); // toEntity 직후 상태 가정
        when(groupRepository.save(any(Group.class))).thenReturn(mockGroup);

        // when
        GroupCreateResponse response = groupService.createGroup(request, userId);

        // then
        assertNotNull(response);
        verify(passwordEncoder, times(1)).encode("1234");
        verify(memberRepository, times(1)).save(any(Member.class)); // 리더 등록 검증
    }

    @Test
    @DisplayName("그룹 생성 실패 - 중복된 그룹명")
    void createGroup_Fail_DuplicateName() {
        // given
        GroupCreateRequest request = new GroupCreateRequest("중복이름", "설명", 10, true, null, false, null);
        when(groupRepository.existsByGroupName("중복이름")).thenReturn(true);

        // when & then
        BusinessException e = assertThrows(BusinessException.class, () -> groupService.createGroup(request, "user1"));
        assertEquals(ErrorCode.DUPLICATE_GROUP_NAME, e.getErrorCode());
    }

    @Test
    @DisplayName("그룹 생성 실패 - 오프라인 그룹인데 지역 정보가 없음")
    void createGroup_Fail_OfflineWithoutRegion() {
        // given
        GroupCreateRequest request = new GroupCreateRequest("이름", "설명", 10, true, null, true, null); // groupOffline = true, Region = null
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
        String userId = "user1";
        GroupJoinRequest request = new GroupJoinRequest("가입인사", null); // 공개 그룹

        Group mockGroup = mock(Group.class);
        when(mockGroup.isGroupPublic()).thenReturn(true);
        when(mockGroup.getCurrentMembers()).thenReturn(1);
        when(mockGroup.getMaxMembers()).thenReturn(10);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));

        User mockUser = mock(User.class);
        when(mockUser.getIsActive()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        when(memberRepository.existsByGroup_GroupIdAndUser_Id(groupId, userId)).thenReturn(false);

        Member mockMember = mock(Member.class);
        when(mockMember.getMemberId()).thenReturn("member-uuid");
        when(mockMember.getMemberStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.save(any(Member.class))).thenReturn(mockMember);

        // when
        GroupJoinResponse response = groupService.joinGroup(groupId, request, userId);

        // then
        assertNotNull(response);
        assertEquals(MemberStatus.PENDING, response.status());
    }

    @Test
    @DisplayName("가입 요청 처리 성공 - 리더가 요청을 승인(APPROVED)하면 멤버 상태가 ACTIVE로 변경된다.")
    void updateMemberRequestStatus_Approve_Success() {
        // given
        String groupId = "group1";
        String memberId = "member1";
        String leaderId = "leader1";

        Group mockGroup = mock(Group.class);
        when(mockGroup.getCurrentMembers()).thenReturn(5);
        when(mockGroup.getMaxMembers()).thenReturn(10);
        when(groupRepository.findByGroupIdForUpdate(groupId)).thenReturn(Optional.of(mockGroup)); // APPROVED인 경우 Lock 적용 메서드 호출

        // 리더 권한 검증 모킹
        Member mockLeader = mock(Member.class);
        when(mockLeader.getMemberRole()).thenReturn(MemberRole.LEADER);
        when(mockLeader.getMemberStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, leaderId)).thenReturn(Optional.of(mockLeader));

        // 대상 멤버 대기 상태 모킹
        Member targetMember = mock(Member.class);
        when(targetMember.getMemberStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId)).thenReturn(Optional.of(targetMember));

        // when
        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(groupId, memberId, leaderId, MemberRequestStatus.APPROVED);

        // then
        assertEquals(MemberRequestStatus.APPROVED, response.status());
        verify(targetMember, times(1)).setMemberStatus(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("가입 요청 처리 실패 - 리더 권한이 없는 유저가 처리 시도 시 예외 발생")
    void updateMemberRequestStatus_Fail_NotLeader() {
        // given
        String groupId = "group1";
        String memberId = "member1";
        String normalUserId = "user2";

        Group mockGroup = mock(Group.class);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup));

        Member mockNormalMember = mock(Member.class);
        when(mockNormalMember.getMemberRole()).thenReturn(MemberRole.MEMBER); // 일반 멤버
        when(mockNormalMember.getMemberStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, normalUserId)).thenReturn(Optional.of(mockNormalMember));

        // when & then
        BusinessException e = assertThrows(BusinessException.class, () ->
                groupService.updateMemberRequestStatus(groupId, memberId, normalUserId, MemberRequestStatus.REJECTED));

        assertEquals(ErrorCode.GROUP_LEADER_ONLY, e.getErrorCode());
    }
}