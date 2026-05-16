package com.bookwheel.server.user.event;

public record UserDeactivatedEvent(
        String userId,
        String mail
) {
}