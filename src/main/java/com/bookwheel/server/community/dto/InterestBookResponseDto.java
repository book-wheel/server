package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Schema(description = "관심 도서 목록 항목")
public record InterestBookResponseDto(
    @Schema(description = "도서 정보 ID", example = "1")
    Long bookInfoId,

    @Schema(description = "도서 ISBN (도서 상세 조회 요청에 사용)", example = "9791165341909")
    String isbn,

    @Schema(description = "도서 제목. 저장된 값이 없으면 \"제목 없음\"이 내려간다.",
        example = "달러구트 꿈 백화점")
    String title,

    @Schema(description = "저자. 저장된 값이 없으면 \"저자 미상\"이 내려간다.", example = "이미예")
    String author,

    @Schema(description = "도서 표지 이미지 URL. 저장된 표지가 없으면 빈 문자열이 내려간다.",
        example = "https://image.aladin.co.kr/cover.jpg")
    String coverImageUrl,

    @Schema(description = "관심 도서로 등록한 일시 (이 값과 bookInfoId 기준으로 최신순 정렬)",
        example = "2026-07-14T12:00:00")
    LocalDateTime interestedAt
) {

    private static final String UNKNOWN_TITLE = "제목 없음";
    private static final String UNKNOWN_AUTHOR = "저자 미상";

    // 도서 기본 정보는 찜 시점에 저장하지만, 외부 API 조회가 실패했거나 표지가 없는 도서는 비어 있을 수 있다.
    // 클라이언트가 항목마다 null 을 분기하지 않도록 응답 계약에서는 null 을 내보내지 않는다.
    // 표지는 이미지 로드 실패 대비가 어차피 필요하므로 대체 URL 대신 빈 문자열로 둔다.
    public InterestBookResponseDto {
        title = StringUtils.hasText(title) ? title : UNKNOWN_TITLE;
        author = StringUtils.hasText(author) ? author : UNKNOWN_AUTHOR;
        coverImageUrl = StringUtils.hasText(coverImageUrl) ? coverImageUrl : "";
    }
}
