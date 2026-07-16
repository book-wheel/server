package com.bookwheel.server.group.dto.search;

import com.bookwheel.server.group.dto.GroupDetailButtonType;
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
        int dday
) {
    public static GroupSearchResponse from(Group group) {
        // 개인화 정보가 없을 때는 기본 버튼 상태를 JOIN으로 내려준다.
        return from(group, GroupDetailButtonType.JOIN);
    }

    public static GroupSearchResponse from(Group group, GroupDetailButtonType bottomButtonType) {
        State normalizedState = normalizeState(group.getGroupState());

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
                .groupStateLabel(mapStateLabel(normalizedState))
                .startDate(group.getStartDate())
                .status(mapStatus(normalizedState))
                .bottomButtonType(bottomButtonType)
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
            case DELETED -> "deleted";
        };
    }

    private static String mapStateLabel(State state) {
        return switch (state) {
            case RECRUITING -> "시작전";
            case IN_PROGRESS -> "진행중";
            case COMPLETE -> "끝";
            case DELETED -> "삭제됨";
        };
    }

    private static int calculateDday(LocalDate startDate) {
        if (startDate == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), startDate);
    }
}
