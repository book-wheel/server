package com.bookwheel.server.group.dto;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import lombok.Builder;

import java.time.LocalDate;

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
        LocalDate startDate
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
                .build();
    }
}
