package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookSearchRankingResult;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class BookSearchRankingService {

    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;

    private final PopularLoanBookRepository popularLoanBookRepository;

    public BookSearchRankingResult rankByPopularity(List<BookSearchResponse> books) {
        if (books == null || books.isEmpty()) {
            return BookSearchRankingResult.kakao(List.of());
        }

        List<String> isbns = books.stream()
            .map(BookSearchResponse::isbn)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        if (isbns.isEmpty()) {
            return BookSearchRankingResult.kakao(books);
        }

        return popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE)
            .map(snapshot -> rankBySnapshot(books, isbns, snapshot))
            .orElseGet(() -> BookSearchRankingResult.kakao(books));
    }

    private BookSearchRankingResult rankBySnapshot(
        List<BookSearchResponse> books,
        List<String> isbns,
        PopularLoanBook snapshot
    ) {
        Map<String, PopularLoanBook> popularityByIsbn = popularLoanBookRepository
            .findBySourceAndStartDateAndEndDateAndIsbnIn(
                SOURCE,
                snapshot.getStartDate(),
                snapshot.getEndDate(),
                isbns
            )
            .stream()
            .filter(popularLoanBook -> StringUtils.hasText(popularLoanBook.getIsbn()))
            .collect(java.util.stream.Collectors.toMap(
                popularLoanBook -> popularLoanBook.getIsbn().trim(),
                Function.identity(),
                this::chooseHigherPopularity
            ));

        if (popularityByIsbn.isEmpty()) {
            return BookSearchRankingResult.kakao(books);
        }

        List<BookSearchResponse> rankedBooks = IntStream.range(0, books.size())
            .mapToObj(index -> {
                BookSearchResponse book = books.get(index);
                String isbn = normalizeIsbn(book.isbn());
                return new RankedBook(book, popularityByIsbn.get(isbn), index);
            })
            .sorted(rankedBookComparator())
            .map(RankedBook::book)
            .toList();

        return BookSearchRankingResult.data4Library(
            rankedBooks,
            snapshot.getStartDate(),
            snapshot.getEndDate()
        );
    }

    private Comparator<RankedBook> rankedBookComparator() {
        return Comparator
            .comparing(RankedBook::hasNoPopularity)
            .thenComparingInt(RankedBook::rank)
            .thenComparing(Comparator.comparingInt(RankedBook::loanCount).reversed())
            .thenComparingInt(RankedBook::kakaoRankScore);
    }

    private PopularLoanBook chooseHigherPopularity(PopularLoanBook current, PopularLoanBook replacement) {
        if (replacement.getRank() < current.getRank()) {
            return replacement;
        }
        if (replacement.getRank().equals(current.getRank())
            && replacement.getLoanCount() > current.getLoanCount()) {
            return replacement;
        }
        return current;
    }

    private String normalizeIsbn(String isbn) {
        if (!StringUtils.hasText(isbn)) {
            return null;
        }
        return isbn.trim();
    }

    private record RankedBook(
        BookSearchResponse book,
        PopularLoanBook popularity,
        int kakaoRankScore
    ) {

        private boolean hasNoPopularity() {
            return popularity == null;
        }

        private int rank() {
            return popularity == null ? Integer.MAX_VALUE : popularity.getRank();
        }

        private int loanCount() {
            return popularity == null ? Integer.MIN_VALUE : popularity.getLoanCount();
        }
    }
}
