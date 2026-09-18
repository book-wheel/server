package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecruitingSchedulePlanSynchronizerTest {

    @Mock
    private RoundRepository roundRepository;

    private final ScheduleCalendarService scheduleCalendarService = new ScheduleCalendarService();

    private RecruitingSchedulePlanSynchronizer synchronizer;

    @Test
    @DisplayName("기존 모집 일정의 목표 인원과 라운드를 모임 최대 인원까지 확장한다")
    void synchronizeToMaxMembers_AppendsMissingRounds() {
        synchronizer = new RecruitingSchedulePlanSynchronizer(roundRepository, scheduleCalendarService);
        Group group = recruitingGroup(5, 2, LocalDate.of(2026, 9, 30));
        Round firstRound = round(group, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(List.of(firstRound));

        boolean changed = synchronizer.synchronizeToMaxMembers(group);

        assertThat(changed).isTrue();
        assertThat(group.getTargetMemberCount()).isEqualTo(5);
        assertThat(group.getGroupRoundCount()).isEqualTo(4);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Round>> roundsCaptor = ArgumentCaptor.forClass(List.class);
        then(roundRepository).should().saveAll(roundsCaptor.capture());
        assertThat(roundsCaptor.getValue())
                .extracting(Round::getRoundNumber)
                .containsExactly(2, 3, 4);
        assertThat(roundsCaptor.getValue())
                .extracting(Round::getStartDate, Round::getEndDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 8, 4),
                                LocalDate.of(2026, 8, 6)
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 8, 7),
                                LocalDate.of(2026, 8, 9)
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 12)
                        )
                );
    }

    @Test
    @DisplayName("목표 인원과 라운드가 이미 정원 기준이면 변경하지 않는다")
    void synchronizeToMaxMembers_DoesNothingWhenAlreadySynchronized() {
        synchronizer = new RecruitingSchedulePlanSynchronizer(roundRepository, scheduleCalendarService);
        Group group = recruitingGroup(3, 3, LocalDate.of(2026, 9, 30));
        List<Round> rounds = List.of(
                round(group, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)),
                round(group, 2, LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 6))
        );
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(rounds);

        boolean changed = synchronizer.synchronizeToMaxMembers(group);

        assertThat(changed).isFalse();
        then(roundRepository).should(never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("확장된 라운드가 설정된 종료 제한일을 넘으면 동기화를 거절한다")
    void synchronizeToMaxMembers_RejectsInsufficientEndDate() {
        synchronizer = new RecruitingSchedulePlanSynchronizer(roundRepository, scheduleCalendarService);
        Group group = recruitingGroup(5, 2, LocalDate.of(2026, 8, 6));
        Round firstRound = round(group, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(List.of(firstRound));

        assertThatThrownBy(() -> synchronizer.synchronizeToMaxMembers(group))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);

        assertThat(group.getTargetMemberCount()).isEqualTo(2);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        then(roundRepository).should(never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Group recruitingGroup(int maxMembers, int targetMemberCount, LocalDate scheduleEndDate) {
        return Group.builder()
                .groupId("group-1")
                .groupName("모집 모임")
                .groupState(State.RECRUITING)
                .startDate(LocalDate.of(2026, 8, 1))
                .readingPeriod(3)
                .scheduleEndDate(scheduleEndDate)
                .maxMembers(maxMembers)
                .targetMemberCount(targetMemberCount)
                .groupRoundCount(targetMemberCount - 1)
                .build();
    }

    private Round round(
            Group group,
            int roundNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return Round.builder()
                .roundId("round-" + roundNumber)
                .group(group)
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
