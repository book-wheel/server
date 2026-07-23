package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "게시물 신고 사유")
@Getter
@RequiredArgsConstructor
public enum PostReportReason {
    SPAM("스팸/홍보성 게시글"),
    ABUSE("욕설/비하/혐오 발언"),
    PORNOGRAPHY("음란물/선정적 내용"),
    COPYRIGHT("저작권 침해 및 도용"),
    OTHER("기타 (상세 내용 참고)");

    private final String description;
}
