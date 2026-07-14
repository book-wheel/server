package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.group.enums.Region;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupUpdateRequest(
        // 일정과 분리된 모임 기본 정보만 수정 요청으로 받는다.
        @NotBlank(message = "그룹 이름은 필수 입력값입니다.")
        @Size(max = 20, message = "그룹 이름은 최대 20자까지 입력할 수 있습니다.")
        String groupName,

        @NotBlank(message = "그룹 한줄소개는 필수 입력값입니다.")
        @Size(max = 50, message = "그룹 한줄소개는 최대 50자까지 입력할 수 있습니다.")
        String groupComment,

        @NotBlank(message = "그룹 규칙은 필수 입력값입니다.")
        String groupRule,

        @NotNull(message = "공개 여부는 필수 입력값입니다.")
        Boolean groupPublic,

        // 비공개 모임 수정 시 매 요청마다 새 평문 비밀번호를 받는다.
        String groupPassword,

        @NotNull(message = "오프라인 여부는 필수 입력값입니다.")
        Boolean groupOffline,

        Region groupRegion,

        // 정원은 현재 ACTIVE 멤버 수보다 작아질 수 없다.
        @NotNull(message = "최대 인원은 필수 입력값입니다.")
        @Min(value = 2, message = "최소 인원은 2명 이상이어야 합니다.")
        Integer maxMembers
) {
}
