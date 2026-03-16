package com.bookwheel.server.wheel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WheelCompleteRequest (
    @Size(min = 1, max = 5, message = "사진은 1~5장 업로드 가능합니다.")
<<<<<<< HEAD
    @NotEmpty(message = "objectKeys는 비어있을 수 없습니다.")
    List<@NotBlank(message = "objectKey는 비어있을 수 없습니다.") String> objectKeys,
=======
    @NotEmpty(message = "objectKeys is required.")
    List<@NotBlank String> objectKeys,
>>>>>>> 129a168622091520f9d98c96938c38ee81b18e28

    @NotBlank(message = "감상평을 남겨주세요.")
    @Size(min = 20, message = "감상평은 최소 20자 이상 작성해야 합니다.")
    String reviewText
) {}
