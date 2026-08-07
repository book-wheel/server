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
    private static final String DESCRIPTION = "전월 인기대출도서 순위 기반 일별 추천";

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
            .findTop30BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
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

    private PopularLoanBook selectDailyBook(List<PopularLoanBook> candidates, LocalDate today) {
        int index = (today.getDayOfMonth() - 1) % candidates.size();
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
            userPK != null && bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK),
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
