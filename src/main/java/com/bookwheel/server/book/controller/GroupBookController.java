package com.bookwheel.server.book.controller;

import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
import com.bookwheel.server.book.dto.OwnBookUpdateResponse;
import com.bookwheel.server.book.dto.OwnBookUpdateRequest;
import com.bookwheel.server.book.service.GroupBookService;
import com.bookwheel.server.common.response.ApiResponse;
import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
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
            description = "그룹에 내가 읽힐 책을 등록합니다. isbn, title은 필수입니다. totalPage는 선택값이며, 입력할 경우 1 이상이어야 합니다."
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
                getUserPK(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "참여 도서 변경",
        description = "그룹에 등록한 참여 도서를 다른 도서로 변경합니다. 그룹이 모집중일 때만 가능하며 다른 사용자가 등록한 책은 불가능합니다."
    )
    @PatchMapping("/{groupId}/books/{ownBookId}")
    public ResponseEntity<ApiResponse<OwnBookUpdateResponse>> updateOwnBook(
        @PathVariable String groupId,
        @PathVariable String ownBookId,
        @RequestBody @Valid OwnBookUpdateRequest request,
        @AuthenticationPrincipal Object principal
    ) {
        OwnBookUpdateResponse response = groupBookService.updateOwnBook(
                groupId,
                ownBookId,
                request, 
                getUserPK(principal)
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
