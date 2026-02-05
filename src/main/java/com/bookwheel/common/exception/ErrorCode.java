package com.bookwheel.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통 에러
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),

    // 인증 관련 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_USER_ID(HttpStatus.BAD_REQUEST, "AUTH_002", "이미 존재하는 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "AUTH_003", "이미 존재하는 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "AUTH_004", "이미 존재하는 닉네임입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH_005", "비밀번호가 일치하지 않습니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_006", "이메일 전송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH_007", "인증번호가 일치하지 않습니다."),
    EXPIRED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH_008", "인증번호가 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_009", "이메일 인증이 완료되지 않았습니다."),
    INACTIVE_USER(HttpStatus.BAD_REQUEST, "AUTH_010", "탈퇴한 사용자입니다."),

    // 책바퀴 비즈니스 에러
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_001", "해당 도서를 찾을 수 없습니다."),
    ALREADY_BORROWED(HttpStatus.BAD_REQUEST, "BOOK_002", "이미 대여 중인 도서입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
