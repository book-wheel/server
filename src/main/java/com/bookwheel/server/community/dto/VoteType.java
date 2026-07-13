package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 사용자의 추천/비추천 선택값")
public enum VoteType {
    RECOMMEND,
    NOT_RECOMMEND;

    public static VoteType fromRecommended(Boolean isRecommended) {
        if (isRecommended == null) {
            return null;
        }
        return isRecommended ? RECOMMEND : NOT_RECOMMEND;
    }
}
