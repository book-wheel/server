package com.bookwheel.server.admin.dto;

public enum PostDeletionReason {
    COPYRIGHT("저작권 문제"),
    ADVERTISING("광고성 사진"),
    IRRELEVANT("상관없는 사진"),
    OTHER("기타");

    private final String description;

    PostDeletionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
