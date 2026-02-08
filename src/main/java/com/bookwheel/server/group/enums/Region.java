package com.bookwheel.server.group.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Region {
    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    GANGWON("강원"),
    CHUNG_BUK("충북"),
    CHUNG_NAM("충남"),
    DAEJEON("대전"),
    SEJONG("세종"),
    JEON_BUK("전북"),
    JEON_NAM("전남"),
    GWANGJU("광주"),
    GYEONG_BUK("경북"),
    GYEONG_NAM("경남"),
    DAEGU("대구"),
    ULSAN("울산"),
    BUSAN("부산"),
    JEJU("제주");

    private final String description;
}