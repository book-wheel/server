package com.bookwheel.server.community.event;

/**
 * 관심 도서로 새로 등록된 시점에 발행된다.
 *
 * 커밋 이후 다른 트랜잭션에서 처리되므로 엔티티가 아니라 ISBN 만 담는다.
 */
public record BookLikedEvent(
        String isbn
) {
}
