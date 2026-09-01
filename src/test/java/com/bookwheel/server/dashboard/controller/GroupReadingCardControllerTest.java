package com.bookwheel.server.dashboard.controller;

import com.bookwheel.server.dashboard.dto.GroupReadingCardResponse;
import com.bookwheel.server.dashboard.dto.MyBookStepResponse;
import com.bookwheel.server.dashboard.dto.MyStepResponse;
import com.bookwheel.server.dashboard.service.GroupReadingCardService;
import com.bookwheel.server.wheel.enums.WheelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupReadingCardController.class)
class GroupReadingCardControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupReadingCardService groupReadingCardService;

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("현재·예정 교환독서 모임 카드를 조회한다")
    void getReadingCards_ReturnsCards() throws Exception {
        GroupReadingCardResponse response = GroupReadingCardResponse.builder()
                .groupId("group-1")
                .groupName("독서 모임")
                .status("scheduled")
                .currentRound(0)
                .totalRound(5)
                .startDate(LocalDate.of(2026, 9, 10))
                .endDate(LocalDate.of(2026, 9, 10))
                .dDay(14)
                .myStep(MyStepResponse.of(
                        "wheel-1",
                        "book-1",
                        WheelStatus.PLANNED,
                        "내가 읽을 책",
                        "https://example.com/book-1.jpg",
                        "전달자"
                ))
                .myBookStep(MyBookStepResponse.of(
                        "book-2",
                        "내가 등록한 책",
                        "https://example.com/book-2.jpg",
                        "작가",
                        "읽을 사람",
                        WheelStatus.PLANNED,
                        null
                ))
                .build();
        given(groupReadingCardService.getReadingCards("user-pk")).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/groups/my/reading-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].groupId").value("group-1"))
                .andExpect(jsonPath("$.data[0].status").value("scheduled"))
                .andExpect(jsonPath("$.data[0].currentRound").value(0))
                .andExpect(jsonPath("$.data[0].dDay").value(14))
                .andExpect(jsonPath("$.data[0].myStep.status").value("PLANNED"))
                .andExpect(jsonPath("$.data[0].myBookStep.bookId").value("book-2"));

        then(groupReadingCardService).should().getReadingCards("user-pk");
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("조회할 모임이 없으면 빈 배열을 반환한다")
    void getReadingCards_ReturnsEmptyArray() throws Exception {
        given(groupReadingCardService.getReadingCards("user-pk")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/groups/my/reading-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
