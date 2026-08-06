package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookSearchRankingResult;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookSearchRankingService {

    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;

    private final PopularLoanBookRepository popularLoanBookRepository;

    public BookSearchRankingResult rankByPopularity(List<BookSearchResponse> books) {
        return rankByPopularity(books, null);
    }

    public BookSearchRankingResult rankByPopularity(List<BookSearchResponse> books, String query) {
        List<BookSearchResponse> kakaoBooks = books == null ? List.of() : books;
        boolean hasSearchQuery = StringUtils.hasText(query);

        if (kakaoBooks.isEmpty() && !hasSearchQuery) {
            return BookSearchRankingResult.kakao(List.of());
        }

        List<String> isbns = kakaoBooks.stream()
            .map(BookSearchResponse::isbn)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        if (isbns.isEmpty() && !hasSearchQuery) {
            return BookSearchRankingResult.kakao(kakaoBooks);
        }

        return popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE)
            .map(snapshot -> rankBySnapshot(kakaoBooks, isbns, query, snapshot))
            .orElseGet(() -> BookSearchRankingResult.kakao(kakaoBooks));
    }

    private BookSearchRankingResult rankBySnapshot(
        List<BookSearchResponse> books,
        List<String> isbns,
        String query,
        PopularLoanBook snapshot
    ) {
        Map<String, PopularLoanBook> popularityByIsbn = findPopularityByIsbn(isbns, snapshot);
        List<BookSearchResponse> mergedBooks = mergeTitleMatchedBooks(
            books,
            query,
            snapshot,
            popularityByIsbn
        );

        if (popularityByIsbn.isEmpty()) {
            return BookSearchRankingResult.kakao(books);
        }

        List<BookSearchResponse> rankedBooks = IntStream.range(0, mergedBooks.size())
            .mapToObj(index -> {
                BookSearchResponse book = mergedBooks.get(index);
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

    private Map<String, PopularLoanBook> findPopularityByIsbn(
        List<String> isbns,
        PopularLoanBook snapshot
    ) {
        if (isbns.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return new LinkedHashMap<>(popularLoanBookRepository
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
            )));
    }

    private List<BookSearchResponse> mergeTitleMatchedBooks(
        List<BookSearchResponse> books,
        String query,
        PopularLoanBook snapshot,
        Map<String, PopularLoanBook> popularityByIsbn
    ) {
        if (!StringUtils.hasText(query)) {
            return books;
        }

        List<BookSearchResponse> mergedBooks = new ArrayList<>(books);
        Set<String> includedIsbns = books.stream()
            .map(BookSearchResponse::isbn)
            .map(this::normalizeIsbn)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        popularLoanBookRepository
            .findTop50BySourceAndStartDateAndEndDateAndTitleContainingIgnoreCaseOrderByRankAscLoanCountDesc(
                SOURCE,
                snapshot.getStartDate(),
                snapshot.getEndDate(),
                query.trim()
            )
            .forEach(popularLoanBook -> {
                String isbn = normalizeIsbn(popularLoanBook.getIsbn());
                if (!StringUtils.hasText(isbn)) {
                    return;
                }

                popularityByIsbn.merge(isbn, popularLoanBook, this::chooseHigherPopularity);
                if (includedIsbns.add(isbn) && StringUtils.hasText(popularLoanBook.getTitle())) {
                    mergedBooks.add(toBookSearchResponse(popularLoanBook));
                }
            });

        return mergedBooks;
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

    private BookSearchResponse toBookSearchResponse(PopularLoanBook popularLoanBook) {
        return new BookSearchResponse(
            popularLoanBook.getTitle(),
            popularLoanBook.getAuthor(),
            popularLoanBook.getPublisher(),
            popularLoanBook.getPublishedDate(),
            popularLoanBook.getThumbnail(),
            popularLoanBook.getIsbn(),
            false
        );
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
