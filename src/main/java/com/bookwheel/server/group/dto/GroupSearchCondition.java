package com.bookwheel.server.group.dto;

import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;

// 데이터를 찾을 조건을 담음 (GET 파라미터)
public record GroupSearchCondition (
        State state, //모집 상태
        String type, //ONLINE, OFFLINE
        Region region, // SEOUL, BUSAN 등
        String keyword // 그룹 검생용
) {}
