package com.bookwheel.server.schedule.service;

import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundScheduler {
    private final GroupScheduleService groupScheduleService;

    @Scheduled(cron = "0 0 0 * * *")
    public void processDailyRoundUpdate() {
        log.info("== 자정 스케줄러 실행: 그룹 라운드 상태 및 책바퀴 업데이트 ==");

        // 1. 오늘 시작하는 그룹 상태를 시작으로 변경
        int updatedGroupCount = groupScheduleService.updateStartedGroupsToInProgress();
        log.info("=> 1단계 완료: {}개의 그룹이 IN_PROGRESS로 변경됨", updatedGroupCount);

        // 2. 오늘 끝나는 라운드의 책바퀴(WheelState) 마감 처리
        int finishGroupCount = groupScheduleService.closeExpiredWheelStates();
        log.info("=> 2단계 완료: {}개의 그룹이 UNFINISHED로 변경됨", finishGroupCount);

        // 3. 오늘 시작하는 라운드의 새로운 책바퀴(WheelState) 생성 및 할당
        int startGroupCount = groupScheduleService.startRoundWheelState();
        log.info("=> 3단계 완료: {}개의 그룹이 시작됨", startGroupCount);

        // 4. 다 끝났으면 COMPLETE로 변경
        int finishedGroupCount = groupScheduleService.closeFinishedGroups();
        log.info("=> 4단계 완료: {}개의 그룹을 종료함", finishedGroupCount);
        log.info("== 자정 스케줄러 실행 종료");
    }
}
