package com.bookwheel.server.notification.scheduler;

import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.schedule.repository.RoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RoundReminderSchedulerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void dispatchDeadlineReminders_QueriesOnlyExecutableInProgressRounds() {
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        given(roundRepository.findExecutableRoundsByEndDate(today.plusDays(3), State.IN_PROGRESS))
                .willReturn(List.of());
        given(roundRepository.findExecutableRoundsByEndDate(today.plusDays(1), State.IN_PROGRESS))
                .willReturn(List.of());
        RoundReminderScheduler scheduler = new RoundReminderScheduler(
                roundRepository,
                eventPublisher,
                FIXED_CLOCK
        );

        scheduler.dispatchDeadlineReminders();

        then(roundRepository).should()
                .findExecutableRoundsByEndDate(today.plusDays(3), State.IN_PROGRESS);
        then(roundRepository).should()
                .findExecutableRoundsByEndDate(today.plusDays(1), State.IN_PROGRESS);
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
