package com.bookwheel.server.notification.scheduler;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.event.RoundDeadlineApproachingEvent;
import com.bookwheel.server.schedule.repository.RoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 매일 09:00 KST 에 D-1 / D-3 마감 임박 라운드를 찾아 알림 이벤트를 발행한다.
 * RoundScheduler(자정 상태 전환)와 분리해 사용자 활동 시간대에 발송한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundReminderScheduler {

    private final RoundRepository roundRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void dispatchDeadlineReminders() {
        LocalDate today = LocalDate.now();
        publishFor(today.plusDays(3), 3);
        publishFor(today.plusDays(1), 1);
    }

    private void publishFor(LocalDate targetEndDate, int daysLeft) {
        List<Round> rounds = roundRepository.findByEndDate(targetEndDate);
        if (rounds.isEmpty()) {
            return;
        }
        for (Round round : rounds) {
            Group group = round.getGroup();
            eventPublisher.publishEvent(new RoundDeadlineApproachingEvent(
                    group.getGroupId(),
                    group.getGroupName(),
                    round.getRoundNumber(),
                    daysLeft
            ));
        }
        log.info("[ReminderScheduler] D-{} 발행: {}건", daysLeft, rounds.size());
    }
}