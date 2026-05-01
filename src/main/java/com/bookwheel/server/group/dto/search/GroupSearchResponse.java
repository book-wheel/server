package com.bookwheel.server.group.dto.search;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import lombok.Builder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Builder
public record GroupSearchResponse(
        String groupId,
        String groupName,
        String groupComment,
        boolean groupOffline,
        Region groupRegion,
        int currentMembers,
        int groupRoundCount,
        int maxMembers,
        State groupState,
        String groupStateLabel,
        LocalDate startDate,
        String status,
        int dday
) {
    public static GroupSearchResponse from(Group group) {
        State normalizedState = normalizeState(group.getGroupState());

        return GroupSearchResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .groupComment(group.getGroupComment())
                .groupRegion(group.getGroupRegion())
                .groupOffline(group.isGroupOffline())
                .currentMembers(group.getCurrentMembers())
                .groupRoundCount(group.getGroupRoundCount())
                .maxMembers(group.getMaxMembers())
                .groupState(normalizedState)
                .groupStateLabel(mapStateLabel(normalizedState))
                .startDate(group.getStartDate())
                .status(mapStatus(normalizedState))
                .dday(calculateDday(group.getStartDate()))
                .build();
    }

    private static State normalizeState(State state) {
        if (state == null) {
            return State.RECRUITING;
        }
        return state;
    }

    private static String mapStatus(State state) {
        return switch (state) {
            case RECRUITING -> "scheduled";
            case IN_PROGRESS -> "active";
            case COMPLETE -> "done";
        };
    }

    private static String mapStateLabel(State state) {
        return switch (state) {
            case RECRUITING -> "시작전";
            case IN_PROGRESS -> "진행중";
            case COMPLETE -> "끝";
        };
    }

    private static int calculateDday(LocalDate startDate) {
        if (startDate == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), startDate);
    }
}
