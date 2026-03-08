package com.bookwheel.server.book.controller;

import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
import com.bookwheel.server.book.service.GroupBookService;
import com.bookwheel.server.common.response.ApiResponse;
import static com.bookwheel.server.common.util.SecurityUtil.getUserId;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "그룹 내부 활동 API (참여 도서 등록, 일정 생성, 읽기 순서 지정)")
@RequestMapping("/api/v1/groups")
public class GroupBookController {
    private final GroupBookService groupBookService;

    @Operation(
            summary = "참여 도서 등록",
            description = "그룹 내 참여 도서를 등록하고 ownbookId를 반환합니다."
    )
    @PostMapping("/{groupId}/books")
    public ResponseEntity<ApiResponse<OwnBookRegisterResponse>> registerOwnBook(
            @PathVariable String groupId,
            @RequestBody @Valid OwnBookRegisterRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        OwnBookRegisterResponse response = groupBookService.registerOwnBook(
                groupId,
                request,
                getUserId(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
