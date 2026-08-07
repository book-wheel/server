package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PopularLoanBookRepository extends JpaRepository<PopularLoanBook, Long> {

    Optional<PopularLoanBook> findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(
        PopularLoanBookSource source
    );

    boolean existsBySourceAndStartDateAndEndDate(
        PopularLoanBookSource source,
        LocalDate startDate,
        LocalDate endDate
    );

    List<PopularLoanBook> findBySourceAndStartDateAndEndDateAndIsbnIn(
        PopularLoanBookSource source,
        LocalDate startDate,
        LocalDate endDate,
        List<String> isbns
    );

    List<PopularLoanBook> findTop31BySourceAndStartDateAndEndDateOrderByRankAscLoanCountDesc(
        PopularLoanBookSource source,
        LocalDate startDate,
        LocalDate endDate
    );

    List<PopularLoanBook> findTop50BySourceAndStartDateAndEndDateAndTitleContainingIgnoreCaseOrderByRankAscLoanCountDesc(
        PopularLoanBookSource source,
        LocalDate startDate,
        LocalDate endDate,
        String title
    );

    void deleteBySourceAndStartDateAndEndDate(
        PopularLoanBookSource source,
        LocalDate startDate,
        LocalDate endDate
    );
}
