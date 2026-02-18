package com.bookwheel.server.admin.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BanReason {
    NOT_SENT("책을 보내지 않음"),
    DAMAGED("책 파손이 심함"),
    NO_CONTACT("연락 두절(잠수)"),
    ETC("기타 사유");

    private final String description;
}
