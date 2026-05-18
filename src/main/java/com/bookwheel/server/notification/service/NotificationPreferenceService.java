package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public NotificationPreference getOrInit(String userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        return preferenceRepository.save(NotificationPreference.defaultsFor(userId));
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return preferenceRepository.findByUserId(userId).orElseThrow(() -> e);
                    }
                });
    }

    @Transactional
    public NotificationPreferenceResponse get(String userId) {
        return NotificationPreferenceResponse.from(getOrInit(userId));
    }

    @Transactional
    public NotificationPreferenceResponse update(String userId, NotificationPreferenceUpdateRequest request) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.defaultsFor(userId)));

        preference.updateCategoryFlags(
                request.groupEnabled(),
                request.roundEnabled(),
                request.communityEnabled(),
                request.pushEnabled()
        );
        if (request.fcmToken() != null) {
            preference.updateFcmToken(request.fcmToken());
        }
        return NotificationPreferenceResponse.from(preference);
    }
}