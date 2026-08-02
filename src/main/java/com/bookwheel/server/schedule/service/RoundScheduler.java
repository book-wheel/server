package com.bookwheel.server.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundScheduler {
    private final GroupScheduleService groupScheduleService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void processDailyRoundUpdate() {
        log.info("== 자정 스케줄러 실행: 그룹 라운드 상태 및 책바퀴 업데이트 ==");

        // 1. 오늘 시작하는 그룹 상태를 시작으로 변경
        int updatedGroupCount = groupScheduleService.updateStartedGroupsToInProgress();
        log.info("=> 1단계 완료: {}개의 그룹이 IN_PROGRESS로 변경됨", updatedGroupCount);

        // 2. 오늘 끝나는 라운드의 책바퀴(WheelState) 마감 처리
        int finishGroupCount = groupScheduleService.closeExpiredWheelStates();
        log.info("=> 2단계 완료: {}개의 그룹이 UNFINISHED로 변경됨", finishGroupCount);

        // 3. 모집 중 미리 만들어 둔 오늘 라운드의 PLANNED 책바퀴를 실제 독서 상태로 활성화
        int startGroupCount = groupScheduleService.startRoundWheelState();
        log.info("=> 3단계 완료: {}개의 그룹이 시작됨", startGroupCount);

        // 4. 다 끝났으면 COMPLETE로 변경
        int finishedGroupCount = groupScheduleService.closeFinishedGroups();
        log.info("=> 4단계 완료: {}개의 그룹을 종료함", finishedGroupCount);
        log.info("== 자정 스케줄러 실행 종료");
    }

    @Scheduled(cron = "30 */5 * * * *", zone = "Asia/Seoul")
    public void retryTodayGroupStarts() {
        // 자정 이후 책 등록 등으로 READY가 된 모임도 시작 당일 안에는 자동 시작할 수 있게 재시도한다.
        // 두 서비스 메서드는 이미 시작된 그룹과 활성화된 WheelState를 건너뛰므로 반복 호출해도 안전하다.
        int updatedGroupCount = groupScheduleService.updateStartedGroupsToInProgress();
        int startedWheelCount = groupScheduleService.startRoundWheelState();
        if (updatedGroupCount > 0 || startedWheelCount > 0) {
            log.info(
                    "시작 당일 재시도 완료: IN_PROGRESS 전환 {}개, 활성화한 책바퀴 {}개",
                    updatedGroupCount,
                    startedWheelCount
            );
        }
    }
}
