package com.bookwheel.server.group.dto;

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
        int maxMembers,
        State groupState,
        LocalDate startDate,
        String status,
        int dday
) {
    public static GroupSearchResponse from(Group group) {
        return GroupSearchResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .groupComment(group.getGroupComment())
                .groupRegion(group.getGroupRegion())
                .groupOffline(group.isGroupOffline())
                .currentMembers(group.getCurrentMembers())
                .maxMembers(group.getMaxMembers())
                .groupState(group.getGroupState())
                .startDate(group.getStartDate())
                .status(mapStatus(group.getGroupState()))
                .dday(calculateDday(group.getStartDate()))
                .build();
    }

    private static String mapStatus(State state) {
        if (state == null) {
            return "scheduled";
        }

        return switch (state) {
            case RECRUITING -> "scheduled";
            case IN_PROGRESS -> "active";
            case COMPLETE -> "done";
        };
    }

    private static int calculateDday(LocalDate startDate) {
        if (startDate == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), startDate);
    }
}
