package com.bookwheel.server.admin.listener;

import com.bookwheel.server.admin.event.UserBannedEvent;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserBanTokenRevocationListener {

    private final AccessTokenRevocationService accessTokenRevocationService;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBanned(UserBannedEvent event) {
        accessTokenRevocationService.revokeAllAccessTokens(event.userPK());
    }
}
