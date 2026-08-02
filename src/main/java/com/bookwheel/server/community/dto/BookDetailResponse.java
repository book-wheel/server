package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도서 상세 정보 응답")
public record BookDetailResponse(
    @Schema(description = "도서 제목", example = "내 남편을 팝니다")
    String title,

    @Schema(description = "저자명", example = "고요한")
    String author,

    @Schema(description = "출판사", example = "나무옆의자")
    String publisher,

    @Schema(description = "도서 상세 설명 (줄거리)", example = "2022년 세계문학상을 수상한 고요한 작가의 신작...")
    String description,

    @Schema(description = "표지 이미지 URL", example = "https://image.aladin.co.kr...")
    String cover,

    @Schema(description = "전체 페이지 수 (쪽수). 외부 도서 API가 페이지 수를 제공하지 않으면 null이 반환됩니다.", example = "236")
    Integer itemPage,

    @Schema(description = "목차 정보 (데이터가 없으면 '등록된 목차 정보가 없습니다.' 반환)", example = "1. 윤해리\n2. 김마틴...")
    String toc,

    @Schema(description = "13자리 ISBN", example = "9791161571188")
    String isbn,

    @Schema(description = "현재 로그인한 사용자의 관심 도서(찜) 여부", example = "true")
    boolean isInterested,

    @Schema(description = "도서관정보나루 기반 도서 이용 분석 정보. "
        + "해당 ISBN의 데이터가 없거나 외부 API 조회에 실패하면 null이 반환되며, 이 경우에도 도서 상세 조회는 정상 응답합니다.")
    BookUsageAnalysisResponse usageAnalysis
) {

    public static BookDetailResponse from(AladinBookSearchResponse.Item item, boolean isInterested) {
        var subInfo = item.subInfo();

        return new BookDetailResponse(
            item.title(),
            item.author(),
            item.publisher(),
            item.description(),
            item.cover(),
            (subInfo != null) ? subInfo.itemPage() : null,
            (subInfo != null && subInfo.toc() != null && !subInfo.toc().isBlank())
                ? subInfo.toc() : "등록된 목차 정보가 없습니다.",
            item.isbn13(),
            isInterested,
            null
        );
    }

    // 이용 분석은 도서 상세 조회의 부가 정보이므로 알라딘 응답 조립과 분리해서 덧붙인다.
    public BookDetailResponse withUsageAnalysis(BookUsageAnalysisResponse usageAnalysis) {
        return new BookDetailResponse(
            title,
            author,
            publisher,
            description,
            cover,
            itemPage,
            toc,
            isbn,
            isInterested,
            usageAnalysis
        );
    }
}
