package com.bookwheel.server.community.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 도서관정보나루 응답을 서비스 응답으로 가공하는 로직 테스트.
 * 실제 인증키나 외부 호출 없이 응답 구조만으로 검증한다.
 */
class BookUsageAnalysisResponseTest {

    private static LibraryNaruUsageAnalysisResponse.LoanGrpItem loanGrp(String age, Integer loanCnt) {
        return new LibraryNaruUsageAnalysisResponse.LoanGrpItem(
            new LibraryNaruUsageAnalysisResponse.LoanGrp(age, loanCnt));
    }

    private static LibraryNaruUsageAnalysisResponse.KeywordItem keyword(String word, Double weight) {
        return new LibraryNaruUsageAnalysisResponse.KeywordItem(
            new LibraryNaruUsageAnalysisResponse.Keyword(word, weight));
    }

    private static LibraryNaruUsageAnalysisResponse.UsageAnalysis usageAnalysis(
        Integer totalLoanCount,
        List<LibraryNaruUsageAnalysisResponse.LoanGrpItem> loanGrps,
        List<LibraryNaruUsageAnalysisResponse.KeywordItem> keywords
    ) {
        return new LibraryNaruUsageAnalysisResponse.UsageAnalysis(
            null,
            (totalLoanCount != null) ? new LibraryNaruUsageAnalysisResponse.Book(totalLoanCount) : null,
            loanGrps,
            keywords
        );
    }

    @Test
    @DisplayName("전체 대출 횟수, 가장 많이 대출한 연령대, 주요 키워드를 모두 추출한다.")
    void from_ExtractsAllValues() {
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            10879,
            List.of(loanGrp("40대", 3041), loanGrp("30대", 1665)),
            List.of(keyword("역사", 10.0), keyword("소설", 8.0), keyword("광주민주화운동", 5.0))
        ));

        assertThat(response).isNotNull();
        assertThat(response.totalLoanCount()).isEqualTo(10879);
        assertThat(response.mostLoanedAgeGroup()).isEqualTo("40대");
        assertThat(response.keywords()).containsExactly("역사", "소설", "광주민주화운동");
    }

    @Test
    @DisplayName("연령대는 ranking이 아니라 loanCnt가 가장 큰 항목으로 선택한다.")
    void from_SelectsAgeGroupByLoanCntNotRanking() {
        // 실제 정보나루 응답에서 ranking 순서와 loanCnt 순서는 일치하지 않는다.
        // (예: 40대 loanCnt=384/ranking=144, 30대 loanCnt=288/ranking=25)
        // ranking을 기준으로 고르면 30대가 잘못 선택되므로 loanCnt 기준인지 확인한다.
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(loanGrp("30대", 288), loanGrp("40대", 384)),
            List.of()
        ));

        assertThat(response.mostLoanedAgeGroup()).isEqualTo("40대");
    }

    @Test
    @DisplayName("연령대와 성별로 나뉘어 온 대출 건수는 연령대 기준으로 합산해서 비교한다.")
    void from_SumsLoanCountByAgeGroup() {
        // 50대 단일 그룹(400)보다 40대 남녀 합(300 + 200)이 크므로 40대가 선택되어야 한다.
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(loanGrp("50대", 400), loanGrp("40대", 300), loanGrp("40대", 200)),
            List.of()
        ));

        assertThat(response.mostLoanedAgeGroup()).isEqualTo("40대");
    }

    @Test
    @DisplayName("연령대가 비어 있는 '연령 미상' 그룹은 가장 많이 대출한 연령대 선택에서 제외한다.")
    void from_IgnoresUnknownAgeGroup() {
        // 실제 응답에는 age가 빈 문자열인 그룹이 존재한다. 제외하지 않으면 빈 문자열이 그대로 응답에 나간다.
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(loanGrp("", 900), loanGrp("40대", 100)),
            List.of()
        ));

        assertThat(response.mostLoanedAgeGroup()).isEqualTo("40대");
    }

    @Test
    @DisplayName("키워드는 가중치가 높은 순으로 최대 3개만 추출하고 가중치는 응답에 포함하지 않는다.")
    void from_ExtractsTopThreeKeywordsByWeight() {
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(),
            List.of(
                keyword("다섯번째", 1.0),
                keyword("첫번째", 12.0),
                keyword("네번째", 3.0),
                keyword("두번째", 8.0),
                keyword("세번째", 5.0)
            )
        ));

        assertThat(response.keywords()).containsExactly("첫번째", "두번째", "세번째");
    }

    @Test
    @DisplayName("키워드가 3개보다 적으면 존재하는 키워드만 반환한다.")
    void from_ReturnsFewerKeywordsWhenNotEnough() {
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(),
            List.of(keyword("역사", 10.0), keyword("소설", 8.0))
        ));

        assertThat(response.keywords()).containsExactly("역사", "소설");
    }

    @Test
    @DisplayName("키워드 앞뒤 공백은 제거하고 값이 없는 키워드는 건너뛴다.")
    void from_TrimsKeywordsAndSkipsBlank() {
        var response = BookUsageAnalysisResponse.from(usageAnalysis(
            1000,
            List.of(),
            List.of(keyword("  역사  ", 10.0), keyword("   ", 9.0), keyword("소설", 8.0))
        ));

        assertThat(response.keywords()).containsExactly("역사", "소설");
    }

    @Test
    @DisplayName("일부 데이터만 있으면 없는 값은 null과 빈 배열로 채워 반환한다.")
    void from_ReturnsPartialData() {
        // 실제로 대출 건수만 있고 대출 그룹과 키워드가 모두 비어 있는 도서가 존재한다.
        var response = BookUsageAnalysisResponse.from(usageAnalysis(460, List.of(), List.of()));

        assertThat(response).isNotNull();
        assertThat(response.totalLoanCount()).isEqualTo(460);
        assertThat(response.mostLoanedAgeGroup()).isNull();
        assertThat(response.keywords()).isEmpty();
    }

    @Test
    @DisplayName("만들 수 있는 정보가 하나도 없으면 null을 반환한다.")
    void from_ReturnsNullWhenNothingAvailable() {
        assertThat(BookUsageAnalysisResponse.from(usageAnalysis(null, List.of(), List.of()))).isNull();
    }

    @Test
    @DisplayName("도서관정보나루 응답 자체가 없으면 null을 반환한다.")
    void from_ReturnsNullWhenResponseIsNull() {
        assertThat(BookUsageAnalysisResponse.from(null)).isNull();
    }
}
