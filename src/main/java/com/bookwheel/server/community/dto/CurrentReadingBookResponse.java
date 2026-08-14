package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

@Schema(description = "도서 메인 화면의 현재 읽고 있는 책 카드")
public record CurrentReadingBookResponse(
    @Schema(description = "교환독서 그룹 홈으로 이동할 때 사용하는 그룹 ID", example = "group-123")
    String groupId,

    @Schema(description = "도서 제목 (표지 이미지가 없거나 로드에 실패했을 때 카드에 표시할 대체 텍스트)",
        example = "달러구트 꿈 백화점")
    String title,

    @Schema(description = "도서 표지 이미지 URL. 저장된 표지가 없으면 빈 문자열이 내려간다.",
        example = "https://image.aladin.co.kr/cover.jpg")
    String coverImageUrl
) {

    // 표지는 등록되지 않은 도서가 있을 수 있어 비어 있을 수 있다.
    // 관심 도서 목록과 같은 규칙으로, 클라이언트가 항목마다 null 을 분기하지 않도록 응답 계약에서는 null 을 내보내지 않는다.
    // 표지가 없을 때 카드가 빈칸이 되지 않도록 제목을 항상 함께 내려준다.
    public CurrentReadingBookResponse {
        coverImageUrl = StringUtils.hasText(coverImageUrl) ? coverImageUrl : "";
    }
}
