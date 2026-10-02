package com.bookwheel.server.admin.listener;

import com.bookwheel.server.admin.event.UserBannedEvent;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserBanTokenRevocationListener {

    private final AccessTokenRevocationService accessTokenRevocationService;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBanned(UserBannedEvent event) {
        try {
            accessTokenRevocationService.revokeAllAccessTokens(event.userPK());
        } catch (DataAccessException exception) {
            // 제재는 이미 커밋되었고 JWT 인증 시 DB 상태로도 차단한다.
            log.error("제재 회원의 Redis 토큰 차단 기록 실패: userPK={}", event.userPK(), exception);
        }
    }
}
