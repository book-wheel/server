package com.bookwheel.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 제재 요청 정보")

public record AdminBanRequest(
    @Schema(description = "제재 유형", example = "PERMANENT", allowableValues = {"THREE_DAYS", "SEVEN_DAYS", "PERMANENT"})
    @NotBlank(message = "제재 유형은 필수입니다.")
    String banType,

    @Schema(description = "제재 사유 코드", example = "NOT_SENT", allowableValues = {"NOT_SENT", "DAMAGED", "NO_CONTACT", "ETC"})
    BanReason reasonCode,

    @Schema(description = "상세 사유 (ETC인 경우 직접 입력)", example = "부적절한 닉네임 사용")
    String detailedReason
) {}
