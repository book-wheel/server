package com.bookwheel.server.community.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;

/**
 * 도서관정보나루 도서별 이용 분석(usageAnalysisList) 응답.
 * 이번 기능에 필요한 전체 대출 횟수, 다대출 이용자 그룹, 키워드 항목만 매핑한다.
 * (대출 추이, 성별, 함께 대출된 도서, 추천 도서 등은 매핑하지 않는다)
 */
public record LibraryNaruUsageAnalysisResponse(
    UsageAnalysis response
) {

    public record UsageAnalysis(
        String errCode,                  // 인증 실패/조회 실패도 HTTP 200으로 내려오므로 실패 판별에 사용한다.
        Book book,                       // 서지 정보 (전체 대출 건수 포함)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<LoanGrpItem> loanGrps,      // 다대출 이용자 그룹 (최근 30일 기준)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<KeywordItem> keywords       // 키워드 정보
    ) {}

    public record Book(
        Integer loanCnt                  // 전체 대출 건수
    ) {}

    // 정보나루 JSON은 목록 항목을 한 번 더 감싸서 내려준다. (loanGrps[].loanGrp)
    public record LoanGrpItem(
        LoanGrp loanGrp
    ) {}

    public record LoanGrp(
        String age,                      // 연령대. "40대" 형태로 그대로 내려온다.
        Integer loanCnt                  // 해당 그룹의 대출 건수
    ) {}

    // keywords[].keyword
    public record KeywordItem(
        Keyword keyword
    ) {}

    public record Keyword(
        String word,                     // 키워드. CDATA 앞뒤 공백이 포함될 수 있다.
        Double weight                    // 가중치
    ) {}
}
