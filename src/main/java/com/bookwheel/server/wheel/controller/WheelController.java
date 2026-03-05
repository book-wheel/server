package com.bookwheel.server.wheel.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.wheel.dto.*;
import com.bookwheel.server.wheel.service.WheelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wheel", description="독서 진행 상태 관리 API")
@RestController
@RequestMapping("/api/v1/wheels")
@RequiredArgsConstructor
public class WheelController {
    private final WheelService wheelService;

    @Operation(summary = "완독 인증", description = "이미지 1~5장과 감상평을 등록하여 완독을 인증합니다.")
    @PatchMapping("/{wheelStateId}/complete")
    public ApiResponse<WheelCompleteResponse> completeReading(
            @PathVariable String wheelStateId,
            @RequestBody @Valid WheelCompleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();

        WheelCompleteResponse response = wheelService.completedReading(userId, wheelStateId, request);

        return ApiResponse.success(response);
    }

}
