package com.bookwheel.common.response;

import com.bookwheel.common.exception.ErrorCode;

public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorResponse error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<?> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    public static ApiResponse<?> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message));
    }
}
