package com.bookwheel.server.book.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
import com.bookwheel.server.book.service.GroupInnerBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupInnerBookController {
    private final GroupInnerBookService groupInnerBookService;

    @PostMapping("/{groupId}/books")
    public ResponseEntity<ApiResponse<OwnBookRegisterResponse>> registerOwnBook(
            @PathVariable String groupId,
            @RequestBody @Valid OwnBookRegisterRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OwnBookRegisterResponse response = groupInnerBookService.registerOwnBook(
                groupId,
                request,
                userDetails.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
