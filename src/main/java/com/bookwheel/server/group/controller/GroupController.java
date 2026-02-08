package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.GroupCreateRequest;
import com.bookwheel.server.group.dto.GroupCreateResponse;
import com.bookwheel.server.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups/making")
public class GroupController {
    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupCreateResponse>> createGroup(@RequestBody @Valid GroupCreateRequest groupCreateRequest) {
        GroupCreateResponse response = groupService.createGroup(groupCreateRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
