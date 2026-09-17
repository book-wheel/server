package com.bookwheel.server.community.support;

import com.bookwheel.server.user.entity.User;

public final class CommunityAuthorDisplay {

    public static final String DELETED_AUTHOR_NAME = "탈퇴한 사용자";

    private CommunityAuthorDisplay() {
    }

    public static boolean isAnonymous(User author) {
        return author == null || !Boolean.TRUE.equals(author.getIsActive());
    }

    public static String displayName(User author) {
        return isAnonymous(author) ? DELETED_AUTHOR_NAME : author.getNickname();
    }

    public static String profileImageKey(User author) {
        return isAnonymous(author) ? null : author.getProfileImageKey();
    }
}
