package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    /**
     * 알림 발송 경로에서 호출된다. 발송 트랜잭션이 이미 write 라서 여기서도 동일 트랜잭션에 합류해
     * 새 row 가 필요한 첫 사용자에 한해 안전하게 INSERT 한다. unique 제약 동시성은 catch+재조회로 보강.
     */
    @Transactional
    public NotificationPreference getOrInit(String userPK) {
        return preferenceRepository.findByUserPK(userPK)
                .orElseGet(() -> {
                    try {
                        return preferenceRepository.save(NotificationPreference.defaultsFor(userPK));
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return preferenceRepository.findByUserPK(userPK).orElseThrow(() -> e);
                    }
                });
    }

    /**
     * Bulk 알림 발송 경로에서 호출된다. IN 절 1회 조회로 기존 preference 를 가져오고
     * 누락된 사용자에 대해서만 defaults 를 saveAll 한다. (N+1 회 SELECT 방지)
     */
    @Transactional
    public Map<String, NotificationPreference> getOrInitAll(Collection<String> userPKs) {
        if (userPKs == null || userPKs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, NotificationPreference> map = new LinkedHashMap<>();
        for (NotificationPreference p : preferenceRepository.findAllByUserPKIn(userPKs)) {
            map.put(p.getUserPK(), p);
        }
        List<NotificationPreference> missing = new ArrayList<>();
        for (String pk : userPKs) {
            if (!map.containsKey(pk)) {
                missing.add(NotificationPreference.defaultsFor(pk));
            }
        }
        if (!missing.isEmpty()) {
            try {
                for (NotificationPreference p : preferenceRepository.saveAll(missing)) {
                    map.put(p.getUserPK(), p);
                }
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 동시성으로 다른 트랜잭션이 먼저 INSERT 한 경우 재조회로 보강
                for (NotificationPreference p : preferenceRepository.findAllByUserPKIn(userPKs)) {
                    map.putIfAbsent(p.getUserPK(), p);
                }
            }
        }
        return map;
    }

    @Transactional
    public NotificationPreferenceResponse get(String userPK) {
        return NotificationPreferenceResponse.from(getOrInit(userPK));
    }

    @Transactional
    public NotificationPreferenceResponse update(String userPK, NotificationPreferenceUpdateRequest request) {
        String requestedToken = request.expoPushToken();
        if (requestedToken != null && !requestedToken.isEmpty()) {
            // Expo Push Token은 앱 설치 단위이므로 계정 전환 시 이전 사용자에게 남아 있으면
            // 다른 사용자의 알림이 같은 기기로 전달될 수 있다. 현재 사용자에게 귀속하기 전에 이전 소유자를 해제한다.
            preferenceRepository.clearExpoPushTokenFromOtherUsers(requestedToken, userPK);
        }

        NotificationPreference preference = preferenceRepository.findByUserPK(userPK)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultsFor(userPK)));

        preference.updateCategoryFlags(
                request.groupEnabled(),
                request.roundEnabled(),
                request.communityEnabled(),
                request.pushEnabled()
        );
        if (requestedToken != null) {
            // 빈 문자열은 프론트가 명시적으로 전달하는 토큰 해제 요청이다.
            preference.updateExpoPushToken(requestedToken.isEmpty() ? null : requestedToken);
        }
        return NotificationPreferenceResponse.from(preference);
    }

    @Transactional
    public void clearExpoPushTokenForUser(String userPK) {
        preferenceRepository.clearExpoPushTokenByUserPK(userPK);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearInvalidExpoPushToken(String expoPushToken) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return;
        }
        preferenceRepository.clearExpoPushTokenByValue(expoPushToken);
    }
}
