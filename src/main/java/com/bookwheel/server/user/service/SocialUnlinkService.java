package com.bookwheel.server.user.service;

import com.bookwheel.server.user.entity.SocialType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUnlinkService {

    @Value("${kakao.admin-key}")
    private String kakaoAdminKey;

    private final RestClient restClient = RestClient.create();

    public void unlink(SocialType socialType, String socialId, String accessToken) {
        if (socialType == SocialType.KAKAO) {
            unlinkKakao(socialId);
        } else if (socialType == SocialType.GOOGLE) {
            unlinkGoogle(accessToken);  // 구글은 토큰 필요
        }
    }

    private void unlinkKakao(String socialId) {
        restClient.post()
                .uri("https://kapi.kakao.com/v1/user/unlink")
                .header("Authorization", "KakaoAK " + kakaoAdminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("target_id_type=user_id&target_id=" + socialId)
                .retrieve()
                .toBodilessEntity();
    }

    private void unlinkGoogle(String accessToken) {
        if (accessToken == null) {
            log.warn("구글 Access Token이 없어 연동 해제를 진행할 수 없습니다.");
            return;
        }

        try {
            restClient.post()
                    .uri("https://oauth2.googleapis.com/revoke?token=" + accessToken)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .retrieve()
                    .toBodilessEntity();
            log.info("구글 연동 해제 성공");
        } catch (Exception e) {
            log.error("구글 연동 해제 실패: {}", e.getMessage());
        }
    }
}