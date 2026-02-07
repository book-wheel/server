package com.bookwheel.server.common.response;

import com.bookwheel.server.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiResponse<T>(
    boolean success,
    @JsonInclude(JsonInclude.Include.NON_NULL) // 데이터가 null이면 JSON 결과에서 제외
    T data,
    @JsonInclude(JsonInclude.Include.NON_NULL) // 에러가 null이면 JSON 결과에서 제외
    ErrorResponse error
) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }


  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }


  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorResponse(code, message));
  }
}
