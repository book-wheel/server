package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Schema(description = "도서관정보나루 기반 도서 이용 분석 정보")
public record BookUsageAnalysisResponse(

    @Schema(description = "도서관 전체 대출 횟수. 데이터가 없으면 null이 반환됩니다.", example = "10879")
    Integer totalLoanCount,

    @Schema(description = "가장 많이 대출한 연령대. 데이터가 없으면 null이 반환됩니다.", example = "40대")
    String mostLoanedAgeGroup,

    @Schema(description = "주요 키워드 (가중치 높은 순, 최대 3개). 데이터가 없으면 빈 배열이 반환됩니다.",
        example = "[\"역사\", \"소설\", \"광주민주화운동\"]")
    List<String> keywords
) {

    private static final int MAX_KEYWORD_COUNT = 3;

    /**
     * 도서관정보나루 응답에서 필요한 값만 뽑아 이용 분석 정보를 만든다.
     * 만들 수 있는 정보가 하나도 없으면 null을 반환한다.
     */
    public static BookUsageAnalysisResponse from(LibraryNaruUsageAnalysisResponse.UsageAnalysis usageAnalysis) {
        if (usageAnalysis == null) {
            return null;
        }

        Integer totalLoanCount = extractTotalLoanCount(usageAnalysis);
        String mostLoanedAgeGroup = extractMostLoanedAgeGroup(usageAnalysis);
        List<String> keywords = extractKeywords(usageAnalysis);

        if (totalLoanCount == null && mostLoanedAgeGroup == null && keywords.isEmpty()) {
            return null;
        }

        return new BookUsageAnalysisResponse(totalLoanCount, mostLoanedAgeGroup, keywords);
    }

    private static Integer extractTotalLoanCount(LibraryNaruUsageAnalysisResponse.UsageAnalysis usageAnalysis) {
        return (usageAnalysis.book() != null) ? usageAnalysis.book().loanCnt() : null;
    }

    /**
     * 정보나루는 대출 그룹을 연령대와 성별로 나눠서 주므로 연령대 기준으로 대출 건수를 합산한 뒤 가장 큰 연령대를 고른다.
     * 순위(ranking)는 대출 건수 순서와 일치하지 않으므로 사용하지 않는다.
     */
    private static String extractMostLoanedAgeGroup(LibraryNaruUsageAnalysisResponse.UsageAnalysis usageAnalysis) {
        if (usageAnalysis.loanGrps() == null) {
            return null;
        }

        Map<String, Integer> loanCountByAgeGroup = new LinkedHashMap<>();
        for (LibraryNaruUsageAnalysisResponse.LoanGrpItem item : usageAnalysis.loanGrps()) {
            LibraryNaruUsageAnalysisResponse.LoanGrp loanGrp = (item != null) ? item.loanGrp() : null;
            if (loanGrp == null || loanGrp.loanCnt() == null) {
                continue;
            }

            // 연령대가 비어 있는 '연령 미상' 그룹은 제외한다.
            String ageGroup = (loanGrp.age() != null) ? loanGrp.age().trim() : "";
            if (ageGroup.isEmpty()) {
                continue;
            }

            loanCountByAgeGroup.merge(ageGroup, loanGrp.loanCnt(), Integer::sum);
        }

        return loanCountByAgeGroup.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    // 가중치가 높은 순서대로 최대 3개의 키워드만 추출한다. (가중치 값 자체는 응답에 포함하지 않는다)
    private static List<String> extractKeywords(LibraryNaruUsageAnalysisResponse.UsageAnalysis usageAnalysis) {
        if (usageAnalysis.keywords() == null) {
            return List.of();
        }

        return usageAnalysis.keywords().stream()
            .filter(Objects::nonNull)
            .map(LibraryNaruUsageAnalysisResponse.KeywordItem::keyword)
            .filter(Objects::nonNull)
            .filter(keyword -> StringUtils.hasText(keyword.word()))
            .sorted(Comparator.comparingDouble(
                (LibraryNaruUsageAnalysisResponse.Keyword keyword) ->
                    (keyword.weight() != null) ? keyword.weight() : 0.0
            ).reversed())
            .limit(MAX_KEYWORD_COUNT)
            .map(keyword -> keyword.word().trim())
            .toList();
    }
}
