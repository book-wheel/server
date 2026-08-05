package com.bookwheel.server.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.dto.member.GroupMemberListResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private MemberService memberService;

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
    @DisplayName("사용자가 활동 중인 모임이 있으면 true를 반환한다.")
    void isUserInGroup_ReturnsTrue_WhenActive() {
        // given (준비 단계)
        String userPK = UUID.randomUUID().toString();
        // Repository가 호출되었을 때, true를 반환하도록 세팅
        given(memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE))
                .willReturn(true);

        // when (실행 단계)
        boolean result = memberService.isUserInGroup(userPK);

        // then (검증 단계)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("사용자가 활동 중인 모임이 없으면 false를 반환한다.")
    void isUserInGroup_ReturnsFalse_WhenNotActive() {
        // given (준비 단계)
        String userPK = UUID.randomUUID().toString();
        // Repository가 호출되었을 때, false를 반환하도록 세팅
        given(memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE))
                .willReturn(false);

        // when (실행 단계)
        boolean result = memberService.isUserInGroup(userPK);

        // then (검증 단계)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("멤버 목록은 읽기 순서 수정에 필요한 memberId와 readOrder를 반환한다")
    void getGroupMembers_ReturnsMemberIdAndReadOrder() {
        String groupId = "group-1";
        Group group = Group.builder().groupId(groupId).groupName("모임").build();
        User user = User.builder()
                .loginId("reader")
                .password("password")
                .nickname("독자")
                .mail("reader@example.com")
                .build();
        Member member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .readOrder(2)
                .build();
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(member));

        GroupMemberListResponse response = memberService.getGroupMembers(groupId);

        assertThat(response.members()).singleElement().satisfies(item -> {
            assertThat(item.memberId()).isEqualTo("member-1");
            assertThat(item.userPK()).isEqualTo(user.getId());
            assertThat(item.readOrder()).isEqualTo(2);
        });
    }
}
