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
        State groupState

        // TODO: 화면 하단 버튼이 [참여하기] 인지, [참여 중] 인지, [모임장 설정] 인지 보여주려면,
        //  "이 API를 호출한 사람이 이 모임의 멤버인가?" 하는 정보가 필요합니다.
        //   User 구현 시 추가 구현 예정
) {
    public static GroupDetailResponse from(Group group) {
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
                .build();
    }
}