package com.bookwheel.server.admin.dto;

import com.bookwheel.server.admin.entity.ReportAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReportProcessRequest(
    @Schema(description = "DISMISS / DELETE_CONTENT / BAN_USER / DELETE_AND_BAN")
    @NotBlank @Pattern(regexp = "DISMISS|DELETE_CONTENT|BAN_USER|DELETE_AND_BAN") String action,
    @Schema(description = "처리 사유 (최대 255자)")
    @NotBlank @Size(max = 255) String reason,
    @Schema(description = "제재 조치일 때만 필수: THREE_DAYS / SEVEN_DAYS / PERMANENT")
    @Pattern(regexp = "THREE_DAYS|SEVEN_DAYS|PERMANENT") String banType
) {
    public ReportAction reportAction() { return ReportAction.valueOf(action); }
}
