package com.bookwheel.server.group.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum State {
    RECRUITING("모집중"),
    IN_PROGRESS("진행중"),
    COMPLETE("완료"),
    DELETED("삭제됨");

    private final String description;
}
