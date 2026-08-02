package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WheelReassignmentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    private WheelReassignmentService wheelReassignmentService;

    @BeforeEach
    void setUp() {
        wheelReassignmentService = new WheelReassignmentService(
                roundRepository,
                ownBookRepository,
                wheelStateRepository,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("미래 재배정 계획은 확정된 실행 라운드만 조회한다")
    void reassignFutureRounds_QueriesOnlyExecutableRounds() {
        String groupId = "group-1";
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of());

        WheelAssignmentPlan plan = wheelReassignmentService.reassignFutureRounds(
                groupId,
                mock(Member.class),
                List.of()
        );

        assertThat(plan.assignments()).isEmpty();
        then(roundRepository).should().findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId);
        then(roundRepository).should(never()).findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
    }

    @Test
    @DisplayName("미래 배정 교체도 확정된 실행 라운드만 조회한다")
    void replaceFuturePlannedAssignments_QueriesOnlyExecutableRounds() {
        String groupId = "group-1";
        given(roundRepository.findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of());

        wheelReassignmentService.replaceFuturePlannedAssignments(
                groupId,
                WheelAssignmentPlan.empty(),
                List.of()
        );

        then(roundRepository).should().findExecutableRoundsByGroupIdOrderByRoundNumberAsc(groupId);
        then(roundRepository).should(never()).findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        then(wheelStateRepository).shouldHaveNoInteractions();
    }
}
