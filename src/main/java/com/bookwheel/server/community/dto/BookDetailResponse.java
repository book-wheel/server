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

    @Schema(description = "전체 페이지 수 (쪽수)", example = "236")
    Integer itemPage,

    @Schema(description = "목차 정보 (데이터가 없으면 '등록된 목차 정보가 없습니다.' 반환)", example = "1. 윤해리\n2. 김마틴...")
    String toc,

    @Schema(description = "13자리 ISBN", example = "9791161571188")
    String isbn
) {

    public static BookDetailResponse from(AladinBookSearchResponse.Item item) {
        var subInfo = item.subInfo();

        return new BookDetailResponse(
            item.title(),
            item.author(),
            item.publisher(),
            item.description(),
            item.cover(),
            (subInfo != null && subInfo.itemPage() != null) ? subInfo.itemPage() : 0,
            (subInfo != null && subInfo.toc() != null && !subInfo.toc().isBlank())
                ? subInfo.toc() : "등록된 목차 정보가 없습니다.",
            item.isbn13()
        );
    }
}
