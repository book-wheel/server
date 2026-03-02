package com.bookwheel.server.community.dto;

import java.time.LocalDateTime;

public record ReviewDetailResponse(
    Long reviewId,
    String bookId,
    String reviewerName,
    boolean isRecommended,
    String comment,
    boolean isHidden,
    int likeCount,
    boolean isLikedByMe,
    LocalDateTime createdAt
) {}
