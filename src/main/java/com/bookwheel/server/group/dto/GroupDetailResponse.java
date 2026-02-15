package com.bookwheel.server.group.dto;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record GroupDetailResponse(
        String groupId,
        String groupName,
        String groupComment,
        String groupRule,
        boolean groupPublic,
        boolean groupOffline,
        Region groupRegion,
        Integer readingPeriod,
        LocalDate startDate,
        int maxMembers,
        int currentMembers,
        int groupRoundCount,
        State groupState,
        GroupDetailButtonType bottomButtonType
) {
    public static GroupDetailResponse from(Group group, GroupDetailButtonType bottomButtonType) {
        return GroupDetailResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .groupComment(group.getGroupComment())
                .groupRule(group.getGroupRule())
                .groupPublic(group.isGroupPublic())
                .groupOffline(group.isGroupOffline())
                .groupRegion(group.getGroupRegion())
                .readingPeriod(group.getReadingPeriod())
                .startDate(group.getStartDate())
                .maxMembers(group.getMaxMembers())
                .currentMembers(group.getCurrentMembers())
                .groupRoundCount(group.getGroupRoundCount())
                .groupState(group.getGroupState())
                .bottomButtonType(bottomButtonType)
                .build();
    }
}
