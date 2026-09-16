package com.bookwheel.server.user.entity;

/**
 * 동의 당사자를 사후 검증할 때 사용하는 식별자의 종류다.
 * 원본 식별자는 저장하지 않고, 종류와 HMAC 결과만 보관한다.
 */
public enum ConsentSubjectIdentifierType {
    EMAIL
}
