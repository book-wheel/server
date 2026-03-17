package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    @Test
    @WithMockUser // 기본 인증된 사용자 모킹 (User ID 파싱은 SecurityUtil 방식에 따라 조정 필요)
    @DisplayName("그룹 생성 API 성공")
    void createGroup_Success() throws Exception {
        // given
        GroupCreateRequest request = new GroupCreateRequest("스프링스터디", "열심히 합시다", "규칙입니다",  true, null, false, null, 5);

        GroupCreateResponse response = new GroupCreateResponse("group-uuid-1234");

        given(groupService.createGroup(any(GroupCreateRequest.class), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/groups/making")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value("group-uuid-1234"));
    }

    @Test
    @WithMockUser
    @DisplayName("그룹 가입 신청 API 성공")
    void joinGroup_Success() throws Exception {
        // given
        String groupId = "group1";
        GroupJoinRequest request = new GroupJoinRequest("가입하고 싶습니다!", null);
        // 상태값 Enum 매핑 가정
        GroupJoinResponse response = new GroupJoinResponse("member-uuid", null); // Enum은 DTO 스펙에 맞게

        given(groupService.joinGroup(eq(groupId), any(GroupJoinRequest.class), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/groups/{groupId}/join", groupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value("member-uuid"));
    }

    @Test
    @WithMockUser
    @DisplayName("조건에 맞는 그룹 목록 조회 API 성공 (Paging)")
    void getGroups_Success() throws Exception {
        // given
        Page<GroupSearchResponse> mockPage = new PageImpl<>(Collections.emptyList());
        given(groupService.getGroups(any(GroupSearchCondition.class), any(Pageable.class))).willReturn(mockPage);

        // when & then
        mockMvc.perform(get("/api/v1/groups")
                        .param("state", "RECRUITING")
                        .param("type", "OFFLINE")
                        .param("region", "SEOUL")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("가입 요청 목록 처리 API 성공")
    void updateMemberRequestStatus_Success() throws Exception {
        // given
        String groupId = "group1";
        String memberId = "member1";
        MemberRequestStatusUpdateRequest request = new MemberRequestStatusUpdateRequest(MemberRequestStatus.APPROVED); // DTO Record/Class 스펙 가정
        MemberRequestStatusUpdateResponse response = new MemberRequestStatusUpdateResponse(memberId, MemberRequestStatus.APPROVED);

        given(groupService.updateMemberRequestStatus(eq(groupId), eq(memberId), any(), eq(MemberRequestStatus.APPROVED))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/groups/{groupId}/members/{memberId}/status", groupId, memberId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}