package com.bookwheel.server.community.repository;

public final class ContentVisibility {
    public static final String POST = """
        not exists (
            select b.blockId from UserBlock b
            where b.blocker.id = :userPK and b.blocked = p.uploader
        )
        """;

    public static final String COMMENT = """
        not exists (
            select b.blockId from UserBlock b
            where b.blocker.id = :userPK and b.blocked = c.user
        )
        """;

    private ContentVisibility() {}
}
