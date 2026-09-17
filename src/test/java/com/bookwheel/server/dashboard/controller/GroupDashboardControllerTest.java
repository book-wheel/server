package com.bookwheel.server.dashboard.controller;

import com.bookwheel.server.dashboard.dto.DashboardResponse;
import com.bookwheel.server.dashboard.dto.MyStepResponse;
import com.bookwheel.server.dashboard.service.GroupDashboardService;
import com.bookwheel.server.wheel.enums.WheelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupDashboardController.class)
class GroupDashboardControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupDashboardService groupDashboardService;

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("대시보드 myStep은 직전 전달자와 원래 책 소유자 닉네임을 반환한다")
    void getDashboard_ReturnsSenderAndOwnerNicknames() throws Exception {
        DashboardResponse response = DashboardResponse.of(
                "독서 모임",
                2,
                5,
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 23),
                6,
                MyStepResponse.of(
                        "wheel-1",
                        "book-1",
                        WheelStatus.READY,
                        "내가 읽을 책",
                        "https://example.com/book.jpg",
                        "직전 전달자",
                        "원래 책 주인"
                ),
                null
        );
        given(groupDashboardService.getDashboard("group-1", "user-pk")).willReturn(response);

        mockMvc.perform(get("/api/v1/groups/group-1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myStep.senderNickname").value("직전 전달자"))
                .andExpect(jsonPath("$.data.myStep.ownerNickname").value("원래 책 주인"));

        then(groupDashboardService).should().getDashboard("group-1", "user-pk");
    }
}
