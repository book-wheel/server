package com.bookwheel.server.common.exception;

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
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_015", "인증이 필요합니다."),

    // JWT 토큰 관련 에러
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_011", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_012", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_013", "리프레시 토큰을 찾을 수 없습니다. (로그인이 필요합니다.)"),
    UNSUPPORTED_TOKEN(HttpStatus.BAD_REQUEST, "AUTH_014", "지원하지 않는 토큰입니다."),

    // 그룹 관련 에러
    DUPLICATE_GROUP_NAME(HttpStatus.BAD_REQUEST, "GROUP_001", "이미 존재하는 그룹 이름입니다."),
    GROUP_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "GROUP_002", "비공개 그룹은 비밀번호가 필수입니다."),
    GROUP_REGION_REQUIRED(HttpStatus.BAD_REQUEST, "GROUP_003", "오프라인 그룹은 지역 입력이 필수입니다."),
    GROUP_NOT_FOUND(HttpStatus.BAD_REQUEST, "GROUP_004", "존재하지 않는 그룹입니다."),
    INVALID_GROUP_PASSWORD(HttpStatus.BAD_REQUEST, "GROUP_005", "비밀번호가 틀렸습니다."),
    GROUP_FULL(HttpStatus.BAD_REQUEST, "GROUP_006", "그룹 정원이 초과되었습니다."),
    GROUP_LEADER_ONLY(HttpStatus.FORBIDDEN, "GROUP_007", "모임장만 가입 요청을 처리할 수 있습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP_008", "해당 멤버를 찾을 수 없습니다."),
    MEMBER_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, "GROUP_009", "대기 중인 가입 요청만 처리할 수 있습니다."),
    DUPLICATE_GROUP_MEMBER(HttpStatus.BAD_REQUEST, "GROUP_010", "이미 가입했거나 가입 요청을 보낸 모임입니다."),
    GROUP_ACTIVE_MEMBER_ONLY(HttpStatus.FORBIDDEN, "GROUP_011", "ACTIVE 멤버만 그룹 내부 기능을 사용할 수 있습니다."),
    GROUP_ROUND_TABLE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "GROUP_012", "round 테이블이 없거나 생성할 수 없습니다."),
    GROUP_SCHEDULE_OWN_BOOK_REQUIRED(HttpStatus.BAD_REQUEST, "GROUP_013", "참여 도서가 없어 일정을 생성할 수 없습니다."),
    GROUP_READING_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "GROUP_014", "독서 주기가 올바르지 않습니다."),
    GROUP_ORDER_MANAGER_ONLY(HttpStatus.FORBIDDEN, "GROUP_015", "ACTIVE LEADER/SUB_LEADER만 읽기 순서를 지정할 수 있습니다."),
    GROUP_ORDER_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "GROUP_016", "isRandom과 memberIds 조합이 올바르지 않습니다."),
    GROUP_ORDER_MEMBER_SET_INVALID(HttpStatus.BAD_REQUEST, "GROUP_017", "수동 지정 memberIds가 ACTIVE 멤버 전체와 일치하지 않습니다."),
    GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE(HttpStatus.BAD_REQUEST, "GROUP_018", "종료일은 시작일보다 빠를 수 없습니다."),
    GROUP_SCHEDULE_END_DATE_MISMATCH(HttpStatus.BAD_REQUEST, "GROUP_019", "요청한 종료일을 계산된 마지막 종료일이 초과했습니다."),
    // 책바퀴 비즈니스 에러
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_001", "해당 도서를 찾을 수 없습니다."),
    ALREADY_BORROWED(HttpStatus.BAD_REQUEST, "BOOK_002", "이미 대여 중인 도서입니다."),

    // 관리자 관련 에러
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_001", "해당 신고 내역을 찾을 수 없습니다."),
    ALREADY_PROCESSED_REPORT(HttpStatus.BAD_REQUEST, "REPORT_002", "이미 처리 완료된 신고입니다."),
    CANNOT_BAN_ADMIN(HttpStatus.BAD_REQUEST, "ADMIN_001", "관리자 계정은 제재할 수 없습니다."),
    ALREADY_BANNED_USER(HttpStatus.BAD_REQUEST, "ADMIN_002", "이미 정지된 사용자입니다."),

    //사진관련 에러
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "PHOTO_001", "해당 사진을 찾을 수 없습니다."),


    DUPLICATE_BOOK_ISBN(HttpStatus.BAD_REQUEST, "BOOK_003", "이미 등록된 ISBN입니다."),
    OWN_BOOK_ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "BOOK_004", "이미 해당 그룹에 참여 도서를 등록했습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
