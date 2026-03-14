package com.bookwheel.server.order.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.order.dto.MemberReadOrderRequest;
import com.bookwheel.server.order.dto.MemberReadOrderResponse;
import com.bookwheel.server.order.service.GroupMemberOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserId;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "그룹 내부 활동 API (참여 도서 등록, 일정 생성, 읽기 순서 지정)")
@RequestMapping("/api/v1/groups")
public class GroupMemberOrderController {
    private final GroupMemberOrderService groupMemberOrderService;

    @Operation(
            summary = "읽기 순서 지정",
            description = "요청 바디에 따라 동작이 달라집니다. " +
                    "isRandom=false면 memberIds 순서대로 결과를 반환하고, " +
                    "isRandom=true면 ACTIVE 멤버를 랜덤으로 섞어 반환합니다. " +
                    "응답 구조는 동일하며 data 배열의 순서 의미가 케이스별로 다릅니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수동 지정: 요청 memberIds 순서와 동일 / 랜덤 지정: 랜덤 순서",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "manualResult",
                                            summary = "수동 지정 결과",
                                            value = "{\n  \"success\": true,\n  \"data\": [\n    {\n      \"order\": 1,\n      \"memberId\": \"member-uuid-A\",\n      \"nickname\": \"책벌레\",\n      \"profileImage\": \"http://...\"\n    },\n    {\n      \"order\": 2,\n      \"memberId\": \"member-uuid-B\",\n      \"nickname\": \"독서왕\",\n      \"profileImage\": \"http://...\"\n    }\n  ]\n}"
                                    ),
                                    @ExampleObject(
                                            name = "randomResult",
                                            summary = "랜덤 지정 결과",
                                            value = "{\n  \"success\": true,\n  \"data\": [\n    {\n      \"order\": 1,\n      \"memberId\": \"member-uuid-B\",\n      \"nickname\": \"독서왕\",\n      \"profileImage\": \"http://...\"\n    },\n    {\n      \"order\": 2,\n      \"memberId\": \"member-uuid-A\",\n      \"nickname\": \"책벌레\",\n      \"profileImage\": \"http://...\"\n    }\n  ]\n}"
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 또는 멤버 집합 불일치 (GROUP_016, GROUP_017)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "순서 지정 권한 없음 (GROUP_015)"
            )
    })
    @PostMapping("/{groupId}/members/order")
    public ResponseEntity<ApiResponse<List<MemberReadOrderResponse>>> assignReadOrder(
            @PathVariable String groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "isRandom=true면 memberIds를 보내지 않습니다. isRandom=false면 memberIds를 필수로 전달합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "manual",
                                            summary = "수동 지정",
                                            value = "{\n  \"isRandom\": false,\n  \"memberIds\": [\n    \"member-uuid-A\",\n    \"member-uuid-B\",\n    \"member-uuid-C\"\n  ]\n}"
                                    ),
                                    @ExampleObject(
                                            name = "random",
                                            summary = "랜덤 지정",
                                            value = "{\n  \"isRandom\": true\n}"
                                    )
                            }
                    )
            )
            @RequestBody @Valid MemberReadOrderRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        List<MemberReadOrderResponse> response = groupMemberOrderService.assignReadOrder(
                groupId,
                request,
                getUserId(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
