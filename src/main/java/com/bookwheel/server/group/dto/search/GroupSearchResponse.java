package com.bookwheel.server.group.dto.search;

import com.bookwheel.server.group.dto.GroupDetailButtonType;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import lombok.Builder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Builder
public record GroupSearchResponse(
        String groupId,
        String groupName,
        String groupComment,
        boolean groupPublic,
        boolean groupOffline,
        Region groupRegion,
        int currentMembers,
        int groupRoundCount,
        int maxMembers,
        State groupState,
        String groupStateLabel,
        LocalDate startDate,
        String status,
        GroupDetailButtonType bottomButtonType,
        Integer dday // null을 내려주기 위한 Integer
) {
    public static GroupSearchResponse from(Group group) {
        // 개인화 정보가 없을 때는 기본 버튼 상태를 JOIN으로 내려준다.
        return from(group, GroupDetailButtonType.JOIN);
    }

    public static GroupSearchResponse from(Group group, GroupDetailButtonType bottomButtonType) {
        // 서비스 밖에서 직접 변환하는 경우에도 일정 API와 동일한 한국 날짜를 기준으로 한다.
        return from(group, bottomButtonType, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    public static GroupSearchResponse from(
            Group group,
            GroupDetailButtonType bottomButtonType,
            LocalDate currentDate
    ) {
        State normalizedState = normalizeState(group.getGroupState());
        boolean rescheduleRequired = isRescheduleRequired(
                normalizedState,
                group.getStartDate(),
                currentDate
        );

        return GroupSearchResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .groupComment(group.getGroupComment())
                .groupPublic(group.isGroupPublic())
                .groupRegion(group.getGroupRegion())
                .groupOffline(group.isGroupOffline())
                .currentMembers(group.getCurrentMembers())
                .groupRoundCount(group.getGroupRoundCount())
                .maxMembers(group.getMaxMembers())
                .groupState(normalizedState)
                .groupStateLabel(mapStateLabel(normalizedState, rescheduleRequired))
                .startDate(group.getStartDate())
                .status(mapStatus(normalizedState, rescheduleRequired))
                .bottomButtonType(bottomButtonType)
                .dday(calculateDday(group.getStartDate(), currentDate, rescheduleRequired))
                .build();
    }

    private static State normalizeState(State state) {
        if (state == null) {
            return State.RECRUITING;
        }
        return state;
    }

    private static String mapStatus(State state, boolean rescheduleRequired) {
        if (rescheduleRequired) {
            return "reschedule_required";
        }
        return switch (state) {
            case RECRUITING -> "scheduled";
            case IN_PROGRESS -> "active";
            case COMPLETE -> "done";
            case DELETED -> "deleted";
        };
    }

    private static String mapStateLabel(State state, boolean rescheduleRequired) {
        if (rescheduleRequired) {
            return "일정 재설정 필요";
        }
        return switch (state) {
            case RECRUITING -> "시작전";
            case IN_PROGRESS -> "진행중";
            case COMPLETE -> "끝";
            case DELETED -> "삭제됨";
        };
    }

    private static Integer calculateDday(
            LocalDate startDate,
            LocalDate currentDate,
            boolean rescheduleRequired
    ) {
        if (rescheduleRequired) {
            return null;
        }
        if (startDate == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(currentDate, startDate);
    }

    private static boolean isRescheduleRequired(
            State state,
            LocalDate startDate,
            LocalDate currentDate
    ) {
        return state == State.RECRUITING
                && startDate != null
                && startDate.isBefore(currentDate);
    }
}
