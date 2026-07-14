package com.bookwheel.server.group.controller;

import com.bookwheel.server.group.dto.GroupDetailButtonType;
import com.bookwheel.server.group.dto.GroupDetailResponse;
import com.bookwheel.server.group.dto.setting.GroupUpdateRequest;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.service.GroupSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupSettingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupSettingService groupSettingService;

    @Test
    @WithMockUser
    @DisplayName("모임 정보 수정 API 성공")
    void updateGroup_Success() throws Exception {
        String groupId = "group-1";
        GroupUpdateRequest request = new GroupUpdateRequest(
                "수정된 모임",
                "수정된 한줄소개",
                "수정된 규칙",
                true,
                null,
                false,
                null,
                5
        );
        GroupDetailResponse response = new GroupDetailResponse(
                groupId,
                request.groupName(),
                request.groupComment(),
                request.groupRule(),
                true,
                false,
                null,
                7,
                null,
                5,
                2,
                0,
                State.RECRUITING,
                GroupDetailButtonType.LEADER_SETTING
        );
        given(groupSettingService.updateGroup(eq(groupId), eq("user"), any(GroupUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupName").value("수정된 모임"))
                .andExpect(jsonPath("$.data.bottomButtonType").value("LEADER_SETTING"));
    }

    @Test
    @WithMockUser
    @DisplayName("모임 정보 수정 시 공개 여부를 누락하면 요청을 거절한다")
    void updateGroup_RejectsMissingGroupPublic() throws Exception {
        String requestWithoutGroupPublic = """
                {
                  "groupName": "수정된 모임",
                  "groupComment": "수정된 한줄소개",
                  "groupRule": "수정된 규칙",
                  "groupOffline": false,
                  "maxMembers": 5
                }
                """;

        mockMvc.perform(patch("/api/v1/groups/{groupId}", "group-1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithoutGroupPublic))
                .andExpect(status().isBadRequest());
    }

}
