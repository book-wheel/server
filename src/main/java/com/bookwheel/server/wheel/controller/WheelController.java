package com.bookwheel.server.wheel.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.wheel.dto.*;
import com.bookwheel.server.wheel.service.WheelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;


@Tag(name = "Group-Inner", description="독서 진행 상태 관리 API")
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
            @AuthenticationPrincipal Object principal) {
        String userPK = getUserPK(principal);

        WheelCompleteResponse response = wheelService.completedReading(userPK, wheelStateId, request);

        return ApiResponse.success(response);
    }

    @Operation(summary = "특정 멤버의 독서 내역 조회 기능", description = "특정 사람이 특정 독서 모임에서 어떤 책들을 읽어왔는지 쭉 나열합니다.")
    @GetMapping("/{groupId}/history/{targetUserPk}")
    public ResponseEntity<ApiResponse<List<WheelHistoryUserResponse>>> getWheelHistoryUser(
            @PathVariable String groupId,
            @PathVariable String targetUserPk,
            @AuthenticationPrincipal Object principal
    ) {
        String userPK = getUserPK(principal);
        List<WheelHistoryUserResponse> response = wheelService.historyReading(userPK, targetUserPk, groupId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "책별 상세 히스토리 조회", description = "특정 책을 거쳐간 모든 멤버의 리뷰를 조회합니다.")
    @GetMapping("/{groupId}/history/books/{ownBookId}")
    public ResponseEntity<ApiResponse<WheelHistoryBookResponse>> getHistoryByBook(
            @PathVariable String groupId,
            @PathVariable String ownBookId,
            @AuthenticationPrincipal Object principal
    ) {
        String userPK = getUserPK(principal);
        WheelHistoryBookResponse response = wheelService.historyReadingBook(userPK, groupId, ownBookId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
