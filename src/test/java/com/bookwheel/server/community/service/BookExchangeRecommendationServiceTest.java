package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookExchangeRecommendationResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BookExchangeRecommendationServiceTest {

    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;
    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 7, 31);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private PopularLoanBookRepository popularLoanBookRepository;

    @Mock
    private BookLikeRepository bookLikeRepository;

    @Mock
    private BookReviewRepository bookReviewRepository;

    @Test
    @DisplayName("2일에는 최신 정보나루 스냅샷 상위 30권 중 두 번째 책을 추천한다")
    void getDailyRecommendation_SelectsBookByDayOfMonth() {
        String userPK = "user-pk";
        PopularLoanBook first = popularBook("9780000000001", "First Book", 1, 1000);
        PopularLoanBook second = popularBook("9780000000002", "Second Book", 2, 900);
        BookReview review = review(second.getIsbn(), "문희연", "좋은 후기", 7);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 2));

        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(first));
        given(popularLoanBookRepository.findTop30BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
            SOURCE,
            START_DATE,
            END_DATE
        )).willReturn(List.of(first, second));
        given(bookLikeRepository.countByBookInfo_Isbn(second.getIsbn())).willReturn(12L);
        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(second.getIsbn(), userPK)).willReturn(true);
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(second.getIsbn()), any(Pageable.class)))
            .willReturn(List.of(review));

        BookExchangeRecommendationResponse response = service.getDailyRecommendation(userPK);

        assertThat(response.recommendationDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(response.basis().source()).isEqualTo("DATA4LIBRARY");
        assertThat(response.basis().sourceName()).isEqualTo("도서관 정보나루");
        assertThat(response.basis().provider()).isEqualTo("국립중앙도서관");
        assertThat(response.basis().sourceUrl()).isEqualTo("https://www.data4library.kr/apiUtilization");
        assertThat(response.basis().startDate()).isEqualTo(START_DATE);
        assertThat(response.basis().endDate()).isEqualTo(END_DATE);
        assertThat(response.book().isbn()).isEqualTo(second.getIsbn());
        assertThat(response.book().title()).isEqualTo("Second Book");
        assertThat(response.book().likeCount()).isEqualTo(12L);
        assertThat(response.book().isInterested()).isTrue();
        assertThat(response.book().review().reviewerName()).isEqualTo("문희연");
        assertThat(response.book().review().comment()).isEqualTo("좋은 후기");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(bookReviewRepository).should()
            .findRepresentativePublicReviewByIsbn(eq(second.getIsbn()), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("일자가 후보 수보다 크면 modulo로 안전하게 순환한다")
    void getDailyRecommendation_RotatesWhenDayExceedsCandidateCount() {
        PopularLoanBook first = popularBook("9780000000001", "First Book", 1, 1000);
        PopularLoanBook second = popularBook("9780000000002", "Second Book", 2, 900);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 31));

        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(first));
        given(popularLoanBookRepository.findTop30BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
            SOURCE,
            START_DATE,
            END_DATE
        )).willReturn(List.of(first, second));
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(first.getIsbn()), any(Pageable.class)))
            .willReturn(List.of());

        BookExchangeRecommendationResponse response = service.getDailyRecommendation("user-pk");

        assertThat(response.recommendationDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(response.book().isbn()).isEqualTo(first.getIsbn());
        assertThat(response.book().review()).isNull();
    }

    @Test
    @DisplayName("정보나루 인기대출 스냅샷이 없으면 출처 메타와 null book을 반환한다")
    void getDailyRecommendation_ReturnsNullBookWhenSnapshotDoesNotExist() {
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 8));
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.empty());

        BookExchangeRecommendationResponse response = service.getDailyRecommendation("user-pk");

        assertThat(response.recommendationDate()).isEqualTo(LocalDate.of(2026, 8, 8));
        assertThat(response.basis().source()).isEqualTo("DATA4LIBRARY");
        assertThat(response.basis().startDate()).isNull();
        assertThat(response.basis().endDate()).isNull();
        assertThat(response.book()).isNull();
        then(popularLoanBookRepository).should()
            .findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE);
        then(popularLoanBookRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("비로그인 userPK로 호출하면 찜 여부 조회를 하지 않고 false를 반환한다")
    void getDailyRecommendation_DoesNotCheckInterestWhenUserPKIsNull() {
        PopularLoanBook first = popularBook("9780000000001", "First Book", 1, 1000);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 1));

        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(first));
        given(popularLoanBookRepository.findTop30BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
            SOURCE,
            START_DATE,
            END_DATE
        )).willReturn(List.of(first));
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(first.getIsbn()), any(Pageable.class)))
            .willReturn(List.of());

        BookExchangeRecommendationResponse response = service.getDailyRecommendation(null);

        assertThat(response.book().isInterested()).isFalse();
        then(bookLikeRepository).should().countByBookInfo_Isbn(first.getIsbn());
        then(bookLikeRepository).shouldHaveNoMoreInteractions();
    }

    private BookExchangeRecommendationService service(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(KST).toInstant(), KST);
        return new BookExchangeRecommendationService(
            popularLoanBookRepository,
            bookLikeRepository,
            bookReviewRepository,
            clock
        );
    }

    private PopularLoanBook popularBook(String isbn, String title, int rank, int loanCount) {
        return PopularLoanBook.builder()
            .isbn(isbn)
            .title(title)
            .author("Author")
            .publisher("Publisher")
            .publishedDate("2026")
            .thumbnail("https://example.com/" + isbn + ".jpg")
            .rank(rank)
            .loanCount(loanCount)
            .collectedAt(LocalDateTime.of(2026, 8, 1, 4, 0))
            .startDate(START_DATE)
            .endDate(END_DATE)
            .source(SOURCE)
            .build();
    }

    private BookReview review(String isbn, String reviewerName, String comment, int likeCount) {
        User reviewer = User.builder()
            .loginId("reviewer")
            .nickname(reviewerName)
            .mail("reviewer@example.com")
            .build();
        return BookReview.builder()
            .reviewId(1L)
            .bookInfo(BookInfo.builder().isbn(isbn).build())
            .reviewer(reviewer)
            .content(comment)
            .isHidden(false)
            .likeCount(likeCount)
            .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
            .build();
    }
}
