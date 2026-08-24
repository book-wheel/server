package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.CurrentReadingBookResponse;
import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.wheel.enums.WheelStatus;
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
    @DisplayName("진행 중인 책 다음에 시작 예정 책과 남은 일수를 반환한다")
    void getCurrentReadingBooks_ReturnsCurrentAndUpcomingBooks() {
        CurrentReadingBookService service = service();
        String userPK = "user-pk";
        List<WheelStateRepository.ReadingBookAssignmentProjection> currentBooks = List.of(
                assignment(
                        "current-group",
                        "현재 읽는 책",
                        "https://example.com/current.jpg",
                        TODAY.minusDays(2)
                )
        );
        List<WheelStateRepository.ReadingBookAssignmentProjection> upcomingBooks = List.of(
                assignment(
                        "upcoming-group",
                        "읽을 예정인 책",
                        "https://example.com/upcoming.jpg",
                        TODAY.plusDays(2)
                )
        );
        given(wheelStateRepository.findCurrentReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.IN_PROGRESS
        )).willReturn(currentBooks);
        given(wheelStateRepository.findUpcomingReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.RECRUITING,
                WheelStatus.PLANNED
        )).willReturn(upcomingBooks);

        CurrentReadingBooksResponse response = service.getCurrentReadingBooks(userPK);

        assertThat(response.books()).hasSize(2);
        assertThat(response.books().get(0)).satisfies(book -> {
            assertThat(book.groupId()).isEqualTo("current-group");
            assertThat(book.upcoming()).isFalse();
            assertThat(book.roundStartDate()).isEqualTo(TODAY.minusDays(2));
            assertThat(book.dday()).isNull();
        });
        assertThat(response.books().get(1)).satisfies(book -> {
            assertThat(book.groupId()).isEqualTo("upcoming-group");
            assertThat(book.upcoming()).isTrue();
            assertThat(book.roundStartDate()).isEqualTo(TODAY.plusDays(2));
            assertThat(book.dday()).isEqualTo(2);
        });
        then(wheelStateRepository).should().findCurrentReadingBooks(userPK, TODAY, MemberStatus.ACTIVE, State.IN_PROGRESS);
        then(wheelStateRepository).should().findUpcomingReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.RECRUITING,
                WheelStatus.PLANNED
        );
    }

    @Test
    @DisplayName("시작 당일의 예정 책은 D-Day를 의미하는 0일로 반환한다")
    void getCurrentReadingBooks_ReturnsZeroDdayOnStartDate() {
        CurrentReadingBookService service = service();
        String userPK = "user-pk";
        given(wheelStateRepository.findCurrentReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.IN_PROGRESS
        )).willReturn(List.of());
        given(wheelStateRepository.findUpcomingReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.RECRUITING,
                WheelStatus.PLANNED
        )).willReturn(List.of(assignment(
                "today-group",
                "오늘 시작할 책",
                null,
                TODAY
        )));

        CurrentReadingBooksResponse response = service.getCurrentReadingBooks(userPK);

        assertThat(response.books()).singleElement().satisfies(book -> {
            assertThat(book.upcoming()).isTrue();
            assertThat(book.dday()).isZero();
        });
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 목록으로 반환한다.")
    void getCurrentReadingBooks_ReturnsEmptyList() {
        CurrentReadingBookService service = service();
        String userPK = "user-pk";
        given(wheelStateRepository.findCurrentReadingBooks(userPK, TODAY, MemberStatus.ACTIVE, State.IN_PROGRESS)).willReturn(List.of());
        given(wheelStateRepository.findUpcomingReadingBooks(
                userPK,
                TODAY,
                MemberStatus.ACTIVE,
                State.RECRUITING,
                WheelStatus.PLANNED
        )).willReturn(List.of());

        CurrentReadingBooksResponse response = service.getCurrentReadingBooks(userPK);

        assertThat(response.books()).isEmpty();
    }

    private CurrentReadingBookService service() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);
        return new CurrentReadingBookService(wheelStateRepository, clock);
    }

    private WheelStateRepository.ReadingBookAssignmentProjection assignment(
            String groupId,
            String title,
            String coverImageUrl,
            LocalDate roundStartDate
    ) {
        return new WheelStateRepository.ReadingBookAssignmentProjection() {
            @Override
            public String getGroupId() {
                return groupId;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public String getCoverImageUrl() {
                return coverImageUrl;
            }

            @Override
            public LocalDate getRoundStartDate() {
                return roundStartDate;
            }
        };
    }
}
