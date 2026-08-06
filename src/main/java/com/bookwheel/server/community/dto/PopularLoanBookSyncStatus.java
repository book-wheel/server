package com.bookwheel.server.community.dto;

/**
 * 인기대출도서 동기화 결과 상태.
 *
 * 정보나루 조회 자체가 실패한 경우는 예외로 전파되므로 여기에 나타나지 않는다.
 * 조회는 성공했지만 적재할 데이터가 없어 기존 스냅샷을 유지한 경우를
 * 정상 적재와 구분하기 위한 값이다.
 */
public enum PopularLoanBookSyncStatus {

    /** 조회한 데이터를 스냅샷으로 적재했다. */
    SYNCED,

    /** 조회 결과가 비어 있어 적재를 건너뛰고 기존 스냅샷을 유지했다. */
    SKIPPED_EMPTY
}