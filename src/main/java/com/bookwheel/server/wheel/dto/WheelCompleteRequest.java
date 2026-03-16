package com.bookwheel.server.wheel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WheelCompleteRequest (
        @Size(min = 1, max = 5, message = "사진은 1~5장 업로드 가능합니다.")
        @NotEmpty(message = "objectKeys is required.")
        List<@NotBlank String> objectKeys,

        @NotBlank(message = "감상평을 남겨주세요.")
        @Size(min = 20, message = "감상평은 최소 20자 이상 작성해야 합니다.")
        String reviewText
) {}