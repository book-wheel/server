package com.bookwheel.server.user.event;

public record UserDeactivatedEvent(
        String userPK,
        String mail
) {
}
