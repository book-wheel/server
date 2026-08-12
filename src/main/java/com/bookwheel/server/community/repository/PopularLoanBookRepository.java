package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PopularLoanBookRepository extends JpaRepository<PopularLoanBook, Long> {

    /**
     * 교환독서 추천이 하루씩 순환하는 후보 수.
     *
     * 아래 findTop31... 의 31과 반드시 같아야 한다.
     * 파생 쿼리 이름에는 상수를 쓸 수 없어 값을 여기에 함께 둔다.
     */
    int RECOMMENDATION_CANDIDATE_COUNT = 31;

    Optional<PopularLoanBook> findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(
        PopularLoanBookSource source
    );

    long countBySourceAndStartDateAndEndDate(
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
