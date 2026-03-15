package com.bookwheel.server.user.dto;

public record ProfileSetupRequest (
    String profileImageKey,
    String comment,
    String nickname
) {}