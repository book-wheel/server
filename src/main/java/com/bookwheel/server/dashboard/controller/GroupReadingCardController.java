package com.bookwheel.server.dashboard.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.dashboard.dto.GroupReadingCardResponse;
import com.bookwheel.server.dashboard.service.GroupReadingCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Home", description = "홈 교환독서 모임 카드 API")
@RequestMapping("api/v1/groups")
public class GroupReadingCardController {
    private final GroupReadingCardService groupReadingCardService;

    @Operation(
            summary = "홈 현재·예정 교환독서 모임 카드 조회",
            description = "ACTIVE 멤버로 참여 중인 RECRUITING, IN_PROGRESS 모임을 책 배정 여부와 관계없이 조회합니다. "
                    + "시작일이 지난 RECRUITING 모임은 reschedule_required로 반환합니다."
    )
    @GetMapping("/my/reading-cards")
    public ApiResponse<List<GroupReadingCardResponse>> getReadingCards(
            @AuthenticationPrincipal Object principal
    ) {
        String userPK = getUserPK(principal);
        return ApiResponse.success(groupReadingCardService.getReadingCards(userPK));
    }
}
