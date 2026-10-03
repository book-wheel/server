package com.bookwheel.server.admin.entity;

public enum ReportAction {
    DISMISS, DELETE_CONTENT, BAN_USER, DELETE_AND_BAN;

    public boolean deletesContent() { return this == DELETE_CONTENT || this == DELETE_AND_BAN; }
    public boolean bansUser() { return this == BAN_USER || this == DELETE_AND_BAN; }
}
