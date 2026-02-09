package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    @PostMapping("/making")
    public ResponseEntity<ApiResponse<GroupCreateResponse>> createGroup(@RequestBody @Valid GroupCreateRequest groupCreateRequest) {
        GroupCreateResponse response = groupService.createGroup(groupCreateRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<GroupSearchResponse>>> getGroups(
            @ModelAttribute GroupSearchCondition condition,
            @PageableDefault(size = 10, sort = "startDate", direction= Sort.Direction.DESC) Pageable pageable
    ) {
        Page<GroupSearchResponse> response = groupService.getGroups(condition, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }


}
