package com.bookwheel.server.schedule.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.group.enums.ScheduleReconfigurationStatus;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupScheduleApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("일정 생성 요청은 기존 endDate 필드로 종료 제한일을 받는다")
    void createRequest_UsesEndDateField() throws Exception {
        GroupScheduleCreateRequest request = objectMapper.readValue(
                """
                        {
                          "startDate": "2026-08-01",
                          "readingPeriod": 7,
                          "endDate": "2026-10-31",
                          "excludedDates": [],
                          "excludedDateRanges": [],
                          "targetMemberCount": 12
                        }
                        """,
                GroupScheduleCreateRequest.class
        );

        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("미래 일정 변경 요청도 기존 endDate 필드로 종료 제한일을 받는다")
    void futureRequest_UsesEndDateField() throws Exception {
        GroupScheduleFutureRequest request = objectMapper.readValue(
                """
                        {
                          "totalRoundCount": 5,
                          "readingPeriod": 7,
                          "endDate": "2026-10-31",
                          "excludedDates": [],
                          "excludedDateRanges": []
                        }
                        """,
                GroupScheduleFutureRequest.class
        );

        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("일정 조회 응답은 종료 제한일을 기존 endDate 필드로 반환한다")
    void response_UsesEndDateField() {
        GroupScheduleResponse response = new GroupScheduleResponse(
                LocalDate.of(2026, 8, 1),
                7,
                LocalDate.of(2026, 10, 31),
                List.of(),
                List.of(),
                GroupScheduleStatus.CONFIGURED,
                ScheduleReconfigurationStatus.NONE,
                12,
                1,
                false,
                List.of(),
                List.of(),
                11,
                0,
                LocalDate.of(2026, 10, 16),
                null,
                null,
                null,
                List.of()
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("endDate").asText()).isEqualTo("2026-10-31");
        assertThat(json.get("scheduleReconfigurationStatus").asText()).isEqualTo("NONE");
        assertThat(json.has("scheduleDeadline")).isFalse();
    }

    @Test
    @DisplayName("배정된 일정은 도서별 완독 히스토리 조회용 ownBookId를 반환한다")
    void assignmentResponse_IncludesOwnBookId() {
        Book book = Book.builder()
                .bookId("book-1")
                .title("테스트 도서")
                .build();
        OwnBook ownBook = OwnBook.builder()
                .ownBookId("own-book-1")
                .book(book)
                .build();
        WheelState wheelState = WheelState.builder()
                .wheelStateId("wheel-state-1")
                .wheelState(WheelStatus.READY)
                .ownBook(ownBook)
                .build();
        Round round = Round.builder()
                .roundId("round-1")
                .roundNumber(1)
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 7))
                .build();

        GroupScheduleAssignmentResponse response = GroupScheduleAssignmentResponse.of(
                round,
                wheelState,
                "책벌레",
                true
        );

        assertThat(response.ownBookId()).isEqualTo("own-book-1");
        assertThat(response.bookId()).isEqualTo("book-1");
    }
}
