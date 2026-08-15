package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.CurrentReadingBookResponse;
import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CurrentReadingBookServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Test
    @DisplayName("현재 읽고 있는 책을 로그인한 사용자와 오늘 날짜로 조회한다.")
    void getCurrentReadingBooks_ReturnsRepositoryResult() {
        CurrentReadingBookService service = service();
        String userPK = "user-pk";
        List<CurrentReadingBookResponse> books = List.of(
            new CurrentReadingBookResponse("group-123", "달러구트 꿈 백화점", "https://example.com/cover.jpg")
        );
        given(wheelStateRepository.findCurrentReadingBooks(userPK, TODAY, MemberStatus.ACTIVE, State.IN_PROGRESS)).willReturn(books);

        CurrentReadingBooksResponse response = service.getCurrentReadingBooks(userPK);

        assertThat(response.books()).containsExactlyElementsOf(books);
        then(wheelStateRepository).should().findCurrentReadingBooks(userPK, TODAY, MemberStatus.ACTIVE, State.IN_PROGRESS);
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록으로 반환한다.")
    void getCurrentReadingBooks_ReturnsEmptyList() {
        CurrentReadingBookService service = service();
        String userPK = "user-pk";
        given(wheelStateRepository.findCurrentReadingBooks(userPK, TODAY, MemberStatus.ACTIVE, State.IN_PROGRESS)).willReturn(List.of());

        CurrentReadingBooksResponse response = service.getCurrentReadingBooks(userPK);

        assertThat(response.books()).isEmpty();
    }

    private CurrentReadingBookService service() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);
        return new CurrentReadingBookService(wheelStateRepository, clock);
    }
}
