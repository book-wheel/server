package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도서 검색 요청 파라미터")
public record BookSearchRequest(
    @Schema(description = "검색어 (제목/저자 등)", example = "달러구트")
    String query,

    @Schema(description = "정렬 기준 (accuracy: 정확도순, latest: 최신순). 미지정 시 accuracy", example = "accuracy")
    String sort,

    @Schema(description = "페이지 번호 (1부터 시작). 미지정 시 1", example = "1")
    Integer page,

    @Schema(description = "페이지 당 검색 결과 개수. 미지정 시 10", example = "10")
    Integer size
) {
    public BookSearchRequest {
        if (sort == null) sort = "accuracy";
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
    }
}
