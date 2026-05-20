package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupSettingServiceTest {

    @InjectMocks
    private GroupSettingService groupSettingService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Test
    @DisplayName("exitMember fails when current round exists but member wheel state is missing")
    void exitMember_Fails_WhenCurrentRoundExistsButWheelStateMissing() {
        String groupId = "group-1";
        String userPK = "user-1";
        String memberId = "member-1";
        String roundId = "round-1";

        Member member = mock(Member.class);
        Round currentRound = Round.builder()
                .roundId(roundId)
                .roundNumber(1)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .build();

        when(member.getMemberRole()).thenReturn(MemberRole.MEMBER);
        when(member.getMemberId()).thenReturn(memberId);
        when(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)).thenReturn(Optional.of(member));
        when(roundRepository.findCurrentRound(groupId, any(LocalDate.class))).thenReturn(Optional.of(currentRound));
        when(wheelStateRepository.findFirstByRoundIdAndMember_MemberId(roundId, memberId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupSettingService.exitMember(groupId, userPK)
        );

        assertEquals(ErrorCode.READING_NOT_COMPLETED, exception.getErrorCode());
        verify(member, never()).exit();
    }
}
