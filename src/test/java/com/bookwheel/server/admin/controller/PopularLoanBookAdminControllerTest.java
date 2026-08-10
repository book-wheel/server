package com.bookwheel.server.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.service.PopularLoanBookSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PopularLoanBookAdminController.class)
class PopularLoanBookAdminControllerTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PopularLoanBookSyncService popularLoanBookSyncService;

    @MockitoBean
    private Clock clock;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 기간을 생략하면 직전 달 인기대출도서를 수동 적재한다")
    void syncPopularLoanBooks_SyncsPreviousMonthWhenPeriodIsOmitted() throws Exception {
        given(clock.instant()).willReturn(Instant.parse("2026-08-05T00:00:00Z"));
        given(clock.getZone()).willReturn(SEOUL_ZONE);
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            1000
        )).willReturn(PopularLoanBookSyncResult.synced(1000));

        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.source").value("DATA4LIBRARY"))
            .andExpect(jsonPath("$.data.sourceName").value("도서관 정보나루 인기대출도서"))
            .andExpect(jsonPath("$.data.startDate").value("2026-07-01"))
            .andExpect(jsonPath("$.data.endDate").value("2026-07-31"))
            .andExpect(jsonPath("$.data.pageSize").value(1000))
            .andExpect(jsonPath("$.data.status").value("SYNCED"))
            .andExpect(jsonPath("$.data.syncedCount").value(1000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("조회 결과가 비어 적재를 건너뛰면 SKIPPED_EMPTY 상태를 응답한다")
    void syncPopularLoanBooks_ReportsSkippedEmptyStatus() throws Exception {
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
            500
        )).willReturn(PopularLoanBookSyncResult.skippedEmpty());

        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-30")
                .param("pageSize", "500")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SKIPPED_EMPTY"))
            .andExpect(jsonPath("$.data.syncedCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 기간과 조회 건수를 지정해 인기대출도서를 수동 적재한다")
    void syncPopularLoanBooks_SyncsRequestedPeriod() throws Exception {
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
            500
        )).willReturn(PopularLoanBookSyncResult.synced(500));

        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-30")
                .param("pageSize", "500")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.startDate").value("2026-06-01"))
            .andExpect(jsonPath("$.data.endDate").value("2026-06-30"))
            .andExpect(jsonPath("$.data.pageSize").value(500))
            .andExpect(jsonPath("$.data.syncedCount").value(500));

        then(popularLoanBookSyncService).should().syncPopularLoanBooks(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
            500
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("추천 후보 수보다 작은 조회 건수는 400을 반환하고 적재하지 않는다")
    void syncPopularLoanBooks_ReturnsBadRequestWhenPageSizeIsBelowCandidateCount() throws Exception {
        // 적재는 기존 스냅샷을 교체하므로, 소량 요청을 허용하면 확보된 추천 후보가 덮여쓰인다.
        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-30")
                .param("pageSize", "1")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isBadRequest());

        then(popularLoanBookSyncService).should(never()).syncPopularLoanBooks(
            any(LocalDate.class),
            any(LocalDate.class),
            anyInt()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("시작일과 종료일 중 하나만 전달하면 400을 반환한다")
    void syncPopularLoanBooks_ReturnsBadRequestWhenOnlyOneDateIsProvided() throws Exception {
        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .param("startDate", "2026-06-01")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("정보나루 호출 실패는 성공 응답으로 숨기지 않고 502를 반환한다")
    void syncPopularLoanBooks_ReturnsBadGatewayWhenData4LibraryCallFails() throws Exception {
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
            500
        )).willThrow(new BusinessException(ErrorCode.DATA4LIBRARY_API_ERROR));

        mockMvc.perform(post("/api/v1/admin/books/popular-loans/sync")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-30")
                .param("pageSize", "500")
                .with(csrf()))
            .andDo(print())
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BOOK_008"));
    }
}
