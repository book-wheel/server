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
import java.util.stream.IntStream;

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
    @DisplayName("추천 도서에 정보나루 출처 메타와 BookWheel 찜/대표 후기 정보를 함께 담아 반환한다")
    void getDailyRecommendation_ReturnsSourceMetadataWithBookDetails() {
        String userPK = "user-pk";
        PopularLoanBook only = popularBook("9780000000001", "First Book", 1, 1000);
        BookReview review = review(only.getIsbn(), "문희연", "좋은 후기", 7);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 2));

        givenSnapshot(only, List.of(only));
        given(bookLikeRepository.countByBookInfo_Isbn(only.getIsbn())).willReturn(12L);
        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(only.getIsbn(), userPK)).willReturn(true);
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(only.getIsbn()), any(Pageable.class)))
            .willReturn(List.of(review));

        BookExchangeRecommendationResponse response = service.getDailyRecommendation(userPK);

        assertThat(response.recommendationDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(response.basis().source()).isEqualTo("DATA4LIBRARY");
        assertThat(response.basis().sourceName()).isEqualTo("도서관 정보나루");
        assertThat(response.basis().provider()).isEqualTo("국립중앙도서관");
        assertThat(response.basis().sourceUrl()).isEqualTo("https://www.data4library.kr/apiUtilization");
        assertThat(response.basis().startDate()).isEqualTo(START_DATE);
        assertThat(response.basis().endDate()).isEqualTo(END_DATE);
        assertThat(response.book().isbn()).isEqualTo(only.getIsbn());
        assertThat(response.book().title()).isEqualTo("First Book");
        assertThat(response.book().likeCount()).isEqualTo(12L);
        assertThat(response.book().isInterested()).isTrue();
        assertThat(response.book().review().reviewerName()).isEqualTo("문희연");
        assertThat(response.book().review().comment()).isEqualTo("좋은 후기");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(bookReviewRepository).should()
            .findRepresentativePublicReviewByIsbn(eq(only.getIsbn()), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("연속한 날짜에는 후보 목록의 바로 다음 책을 추천한다")
    void getDailyRecommendation_MovesToNextCandidateOnTheNextDay() {
        List<PopularLoanBook> candidates = candidates(31);

        int todayIndex = indexOfIsbn(candidates, recommendedIsbn(LocalDate.of(2026, 8, 10)));
        int tomorrowIndex = indexOfIsbn(candidates, recommendedIsbn(LocalDate.of(2026, 8, 11)));

        assertThat(tomorrowIndex).isEqualTo((todayIndex + 1) % candidates.size());
    }

    @Test
    @DisplayName("31일까지 있는 달에도 1일과 31일에 서로 다른 책을 추천한다")
    void getDailyRecommendation_DoesNotRepeatWithinThirtyOneDayMonth() {
        candidates(31);

        String firstDayIsbn = recommendedIsbn(LocalDate.of(2026, 8, 1));
        String lastDayIsbn = recommendedIsbn(LocalDate.of(2026, 8, 31));

        assertThat(firstDayIsbn).isNotEqualTo(lastDayIsbn);
    }

    @Test
    @DisplayName("2월을 지나도 순환이 끊기지 않아 31일 동안 모든 후보가 한 번씩 노출된다")
    void getDailyRecommendation_ExposesEveryCandidateAcrossShortMonth() {
        List<PopularLoanBook> candidates = candidates(31);
        LocalDate start = LocalDate.of(2026, 2, 1);

        List<String> recommendedIsbns = IntStream.range(0, 31)
            .mapToObj(dayOffset -> recommendedIsbn(start.plusDays(dayOffset)))
            .toList();

        assertThat(recommendedIsbns)
            .doesNotHaveDuplicates()
            .containsExactlyInAnyOrderElementsOf(candidates.stream().map(PopularLoanBook::getIsbn).toList());
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
    @DisplayName("찜하지 않은 사용자에게는 isInterested가 false로 내려간다")
    void getDailyRecommendation_ReturnsFalseInterestWhenUserDidNotLikeBook() {
        String userPK = "user-pk";
        PopularLoanBook first = popularBook("9780000000001", "First Book", 1, 1000);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 1));

        givenSnapshot(first, List.of(first));
        given(bookLikeRepository.countByBookInfo_Isbn(first.getIsbn())).willReturn(3L);
        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(first.getIsbn(), userPK)).willReturn(false);
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(first.getIsbn()), any(Pageable.class)))
            .willReturn(List.of());

        BookExchangeRecommendationResponse response = service.getDailyRecommendation(userPK);

        assertThat(response.book().likeCount()).isEqualTo(3L);
        assertThat(response.book().isInterested()).isFalse();
    }

    @Test
    @DisplayName("추천 기준 문구에 매월 1일 새벽 4시 갱신 안내가 포함된다")
    void getDailyRecommendation_DescribesRefreshTiming() {
        PopularLoanBook first = popularBook("9780000000001", "First Book", 1, 1000);
        BookExchangeRecommendationService service = service(LocalDate.of(2026, 8, 1));

        givenSnapshot(first, List.of(first));
        given(bookReviewRepository.findRepresentativePublicReviewByIsbn(eq(first.getIsbn()), any(Pageable.class)))
            .willReturn(List.of());

        BookExchangeRecommendationResponse response = service.getDailyRecommendation("user-pk");

        assertThat(response.basis().description()).contains("매월 1일 새벽 4시");
    }

    private void givenSnapshot(PopularLoanBook latestSnapshot, List<PopularLoanBook> candidates) {
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(latestSnapshot));
        given(popularLoanBookRepository.findTop31BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
            SOURCE,
            START_DATE,
            END_DATE
        )).willReturn(candidates);
    }

    /** 순위 1위부터 count위까지의 후보를 만들고, 최신 스냅샷 조회까지 함께 스텁한다. */
    private List<PopularLoanBook> candidates(int count) {
        List<PopularLoanBook> candidates = IntStream.rangeClosed(1, count)
            .mapToObj(rank -> popularBook(String.format("978%010d", rank), "Book " + rank, rank, 1000 - rank))
            .toList();
        givenSnapshot(candidates.get(0), candidates);
        return candidates;
    }

    private String recommendedIsbn(LocalDate today) {
        return service(today).getDailyRecommendation("user-pk").book().isbn();
    }

    private int indexOfIsbn(List<PopularLoanBook> candidates, String isbn) {
        return IntStream.range(0, candidates.size())
            .filter(index -> candidates.get(index).getIsbn().equals(isbn))
            .findFirst()
            .orElseThrow();
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
