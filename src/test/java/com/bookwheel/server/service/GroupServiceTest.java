package com.bookwheel.server.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.GroupCreateRequest;
import com.bookwheel.server.group.dto.GroupCreateResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupService;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    @DisplayName("createGroup creates leader member")
    void createGroup_success() {
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("book-group")
                .groupComment("comment")
                .groupRule("rule")
                .groupPublic(true)
                .groupOffline(false)
                .readingPeriod(7)
                .startDate(LocalDate.now().plusDays(1))
                .maxMembers(5)
                .build();

        User user = User.builder()
                .userId("leader-user")
                .password("pw")
                .nickname("leader")
                .mail("leader@mail.com")
                .role(Role.USER)
                .build();

        Group savedGroup = request.toEntity();

        when(groupRepository.existsByGroupName("book-group")).thenReturn(false);
        when(userRepository.findByUserId("leader-user")).thenReturn(Optional.of(user));
        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);

        GroupCreateResponse response = groupService.createGroup(request, "leader-user");

        assertThat(response.groupId()).isEqualTo(savedGroup.getGroupId());

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member leaderMember = memberCaptor.getValue();
        assertThat(leaderMember.getMemberRole()).isEqualTo(MemberRole.LEADER);
        assertThat(leaderMember.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(leaderMember.getUser().getUserId()).isEqualTo("leader-user");
    }

    @Test
    @DisplayName("createGroup fails when group name already exists")
    void createGroup_duplicateName_fail() {
        GroupCreateRequest request = GroupCreateRequest.builder()
                .groupName("book-group")
                .groupComment("comment")
                .groupRule("rule")
                .groupPublic(true)
                .groupOffline(false)
                .readingPeriod(7)
                .startDate(LocalDate.now().plusDays(1))
                .maxMembers(5)
                .build();

        when(groupRepository.existsByGroupName("book-group")).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(request, "leader-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_GROUP_NAME);
    }
}
