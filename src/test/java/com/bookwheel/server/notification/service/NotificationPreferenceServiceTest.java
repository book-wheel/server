package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.repository.NotificationPreferenceRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    @Test
    @DisplayName("유효한 Expo Push Token을 등록하거나 갱신한다")
    void updateRegistersExpoPushToken() {
        NotificationPreference preference = NotificationPreference.defaultsFor("userPK");
        given(preferenceRepository.findByUserPK("userPK")).willReturn(Optional.of(preference));

        NotificationPreferenceResponse response = preferenceService.update(
                "userPK",
                request("ExponentPushToken[valid_token]")
        );

        assertThat(response.expoPushToken()).isEqualTo("ExponentPushToken[valid_token]");
        assertThat(preference.getExpoPushToken()).isEqualTo("ExponentPushToken[valid_token]");
    }

    @Test
    @DisplayName("빈 문자열을 전달하면 저장된 Expo Push Token을 해제한다")
    void updateClearsExpoPushTokenWithEmptyString() {
        NotificationPreference preference = NotificationPreference.builder()
                .userPK("userPK")
                .expoPushToken("ExpoPushToken[old_token]")
                .build();
        given(preferenceRepository.findByUserPK("userPK")).willReturn(Optional.of(preference));

        NotificationPreferenceResponse response = preferenceService.update("userPK", request(""));

        assertThat(response.expoPushToken()).isNull();
        assertThat(preference.getExpoPushToken()).isNull();
    }

    @Test
    @DisplayName("토큰 필드를 생략하면 기존 Expo Push Token을 유지한다")
    void updateKeepsExpoPushTokenWhenOmitted() {
        NotificationPreference preference = NotificationPreference.builder()
                .userPK("userPK")
                .expoPushToken("ExpoPushToken[current_token]")
                .build();
        given(preferenceRepository.findByUserPK("userPK")).willReturn(Optional.of(preference));

        NotificationPreferenceResponse response = preferenceService.update("userPK", request(null));

        assertThat(response.expoPushToken()).isEqualTo("ExpoPushToken[current_token]");
    }

    @Test
    @DisplayName("Expo 토큰 두 형식과 해제용 빈 문자열만 검증을 통과한다")
    void requestValidatesExpoPushTokenContract() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(request("ExpoPushToken[expo_token]"))).isEmpty();
            assertThat(validator.validate(request("ExponentPushToken[exponent_token]"))).isEmpty();
            assertThat(validator.validate(request(""))).isEmpty();
            assertThat(validator.validate(request(null))).isEmpty();
            assertThat(validator.validate(request("native-fcm-token"))).isNotEmpty();
        }
    }

    private NotificationPreferenceUpdateRequest request(String expoPushToken) {
        return new NotificationPreferenceUpdateRequest(null, null, null, null, expoPushToken);
    }
}
