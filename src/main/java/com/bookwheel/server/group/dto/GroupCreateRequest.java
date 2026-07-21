package com.bookwheel.server.group.dto;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record GroupCreateRequest(
        @NotBlank(message="그룹 이름은 필수 입력값입니다.")
        String groupName,

        @NotBlank(message="그룹 한줄소개는 필수 입력값입니다.")
        String groupComment,

        @NotBlank(message="그룹 규칙은 필수 입력값입니다.")
        String groupRule,

        @NotNull(message = "최대 인원은 필수 입력값입니다.")
        boolean groupPublic,

        String groupPassword, // 공개방이면 null 가능

        @NotNull(message = "오프라인 여부는 필수 입력값입니다.")
        boolean groupOffline,

        Region groupRegion, // 온라인이면 null 가능

        @NotNull(message = "기간은 필수 입력값입니다.")
        @Min(value = 1, message = "기간은 최소 1일 이상이어야 합니다.")
        Integer readingPeriod,

        @NotNull(message = "시작일은 필수 입력값입니다.")
        // 자정 스케줄러를 놓치지 않도록 최초 모임 생성도 내일부터 시작하게 한다.
        @Future(message = "시작일은 내일부터 설정할 수 있습니다.")
        LocalDate startDate,

        @NotNull(message = "최대 인원은 필수 입력값입니다.")
        @Min(value = 2, message = "최소 인원은 2명 이상이어야 합니다.")
        Integer maxMembers
) {
    public Group toEntity() {
        return Group.builder()
                .groupId(UUID.randomUUID().toString())
                .groupName(this.groupName)
                .groupComment(this.groupComment)
                .groupRule(this.groupRule)
                .groupPublic(this.groupPublic)
                .groupPassword(this.groupPassword)
                .groupOffline(this.groupOffline)
                .groupRegion(this.groupRegion)
                .readingPeriod(this.readingPeriod)
                .startDate(this.startDate)
                .maxMembers(this.maxMembers)
                .build();
    }
}
