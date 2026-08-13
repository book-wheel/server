package com.bookwheel.server.group.controller;

import java.time.LocalDate;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.dto.member.*;
import com.bookwheel.server.group.dto.search.*;
import com.bookwheel.server.group.dto.setting.*;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.group.service.GroupService;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.service.MemberService;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private MemberService memberService;

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("이유: " + cause.getMessage());
        }
    };

    @Test
    @WithMockUser // 기본 인증된 사용자 모킹
    @DisplayName("그룹 생성 API 성공")
    void createGroup_Success() throws Exception {
        // given
        GroupCreateRequest request = new GroupCreateRequest(
                "스프링스터디",
                "열심히 합시다",
                "규칙입니다",
                true,
                null,
                false,
                null,
                7,
                LocalDate.now().plusDays(1),
                5
        );
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
    @DisplayName("그룹 생성 시 최대 인원은 12명을 초과할 수 없다")
    void createGroup_RejectsMaxMembersAboveLimit() throws Exception {
        String request = """
                {
                  "groupName": "스프링스터디",
                  "groupComment": "열심히 합시다",
                  "groupRule": "규칙입니다",
                  "groupPublic": true,
                  "groupOffline": false,
                  "readingPeriod": 7,
                  "startDate": "2027-01-01",
                  "maxMembers": 13
                }
                """;

        mockMvc.perform(post("/api/v1/groups/making")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        then(groupService).shouldHaveNoInteractions();
    }

    @Test
    @WithMockUser
    @DisplayName("그룹 가입 신청 API 성공")
    void joinGroup_Success() throws Exception {
        // given
        String groupId = "group1";
        GroupJoinRequest request = new GroupJoinRequest("가입하고 싶습니다!", "1234");
        GroupJoinResponse response = new GroupJoinResponse("member-uuid", MemberStatus.PENDING);

        given(groupService.joinGroup(eq(groupId), any(GroupJoinRequest.class), any()))
                .willReturn(response);

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
        given(groupService.getGroups(any(GroupSearchCondition.class), any(Pageable.class), eq("user"))).willReturn(mockPage);

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
    @DisplayName("조건에 맞는 그룹 목록 조회 API 성공 - 비로그인")
    @WithAnonymousUser
    void getGroups_Guest_Success() throws Exception {
        // given
        Page<GroupSearchResponse> mockPage = new PageImpl<>(Collections.emptyList());
        given(groupService.getGroups(any(GroupSearchCondition.class), any(Pageable.class), isNull())).willReturn(mockPage);

        // when & then
        mockMvc.perform(get("/api/v1/groups")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("그룹 멤버 목록은 현재 라운드의 배정 도서와 독서 상태를 반환한다")
    void getGroupMembers_Success() throws Exception {
        String groupId = "group-1";
        GroupMemberCurrentRoundAssignmentResponse assignment =
                new GroupMemberCurrentRoundAssignmentResponse(
                        "wheel-1",
                        "book-1",
                        "소년이 온다",
                        "https://example.com/cover.jpg",
                        WheelStatus.READING
                );
        GroupMemberResponse member = GroupMemberResponse.builder()
                .memberId("member-1")
                .userPK("user-1")
                .nickname("독자")
                .profileImageUrl("https://example.com/profile.jpg")
                .role(MemberRole.MEMBER)
                .readOrder(1)
                .currentRoundAssignment(assignment)
                .build();
        GroupMemberListResponse response = GroupMemberListResponse.from(
                new GroupCurrentRoundResponse("round-2", 2),
                java.util.List.of(member)
        );
        given(memberService.getGroupMembers(groupId)).willReturn(response);

        mockMvc.perform(get("/api/v1/groups/{groupId}/members", groupId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.currentRound.roundId").value("round-2"))
                .andExpect(jsonPath("$.data.currentRound.roundNumber").value(2))
                .andExpect(jsonPath("$.data.members[0].userPK").value("user-1"))
                .andExpect(jsonPath("$.data.members[0].readOrder").value(1))
                .andExpect(jsonPath("$.data.members[0].currentRoundAssignment.wheelStateId").value("wheel-1"))
                .andExpect(jsonPath("$.data.members[0].currentRoundAssignment.bookId").value("book-1"))
                .andExpect(jsonPath("$.data.members[0].currentRoundAssignment.bookTitle").value("소년이 온다"))
                .andExpect(jsonPath("$.data.members[0].currentRoundAssignment.coverImage")
                        .value("https://example.com/cover.jpg"))
                .andExpect(jsonPath("$.data.members[0].currentRoundAssignment.readingStatus")
                        .value("READING"));
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 그룹의 멤버 목록 조회는 GROUP_NOT_FOUND를 반환한다")
    void getGroupMembers_ReturnsGroupNotFound_WhenGroupDoesNotExist() throws Exception {
        String groupId = "not-existing-group";
        given(memberService.getGroupMembers(groupId))
                .willThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        mockMvc.perform(get("/api/v1/groups/{groupId}/members", groupId))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GROUP_004"))
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 그룹입니다."));
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
