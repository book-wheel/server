package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookExchangeRecommendationBasis;
import com.bookwheel.server.community.dto.BookExchangeRecommendationBook;
import com.bookwheel.server.community.dto.BookExchangeRecommendationResponse;
import com.bookwheel.server.community.dto.BookExchangeRecommendationReview;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookExchangeRecommendationService {

    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;
    private static final String BASIS_TYPE = "DAILY_ROTATION";
    private static final String SOURCE_NAME = "도서관 정보나루";
    private static final String PROVIDER = "국립중앙도서관";
    private static final String SOURCE_URL = "https://www.data4library.kr/apiUtilization";
    // 추천 후보는 매월 1일 새벽 4시(KST) 적재 이후 새 스냅샷으로 교체되므로, 갱신 시점을 문구로 함께 안내한다.
    private static final String DESCRIPTION =
        "전월 인기대출도서 순위 기반 일별 추천 (매월 1일 새벽 4시 기준으로 추천 도서 목록이 갱신됩니다)";

    private final PopularLoanBookRepository popularLoanBookRepository;
    private final BookLikeRepository bookLikeRepository;
    private final BookReviewRepository bookReviewRepository;
    private final Clock clock;

    public BookExchangeRecommendationResponse getDailyRecommendation(String userPK) {
        LocalDate today = LocalDate.now(clock);

        PopularLoanBook latestSnapshot = popularLoanBookRepository
            .findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE)
            .orElse(null);

        if (latestSnapshot == null) {
            return new BookExchangeRecommendationResponse(today, basis(null, null), null);
        }

        BookExchangeRecommendationBasis basis = basis(
            latestSnapshot.getStartDate(),
            latestSnapshot.getEndDate()
        );
        List<PopularLoanBook> candidates = popularLoanBookRepository
            .findTop31BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
                SOURCE,
                latestSnapshot.getStartDate(),
                latestSnapshot.getEndDate()
            );

        if (candidates.isEmpty()) {
            return new BookExchangeRecommendationResponse(today, basis, null);
        }

        PopularLoanBook selectedBook = selectDailyBook(candidates, today);
        return new BookExchangeRecommendationResponse(
            today,
            basis,
            toRecommendationBook(selectedBook, userPK)
        );
    }

    /**
     * 후보 목록을 날짜순으로 순환해 오늘 추천할 책을 고른다.
     *
     * 일자(1~31) 대신 epochDay를 쓰는 이유는 달마다 길이가 달라도 순환이 끊기지 않게 하기 위해서다.
     * 일자 기준이면 28일까지인 2월에는 29번째, 30번째 후보가 한 번도 노출되지 않는다.
     * 후보를 31권으로 두는 이유는 31일인 달에도 같은 달 안에서 같은 책이 다시 나오지 않게 하기 위해서다.
     * (후보가 30권이면 1일과 31일이 정확히 30일 차이라 같은 책으로 겹친다.)
     */
    private PopularLoanBook selectDailyBook(List<PopularLoanBook> candidates, LocalDate today) {
        int index = Math.floorMod(today.toEpochDay(), candidates.size());
        return candidates.get(index);
    }

    private BookExchangeRecommendationBook toRecommendationBook(PopularLoanBook book, String userPK) {
        String isbn = book.getIsbn();
        return new BookExchangeRecommendationBook(
            isbn,
            book.getTitle(),
            book.getAuthor(),
            book.getThumbnail(),
            book.getRank(),
            book.getLoanCount(),
            bookLikeRepository.countByBookInfo_Isbn(isbn),
            bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK),
            findRepresentativeReview(isbn)
        );
    }

    private BookExchangeRecommendationReview findRepresentativeReview(String isbn) {
        return bookReviewRepository.findRepresentativePublicReviewByIsbn(
                isbn,
                PageRequest.of(0, 1)
            )
            .stream()
            .findFirst()
            .map(BookExchangeRecommendationReview::from)
            .orElse(null);
    }

    private BookExchangeRecommendationBasis basis(LocalDate startDate, LocalDate endDate) {
        return new BookExchangeRecommendationBasis(
            BASIS_TYPE,
            SOURCE.name(),
            SOURCE_NAME,
            PROVIDER,
            SOURCE_URL,
            startDate,
            endDate,
            DESCRIPTION
        );
    }
}
