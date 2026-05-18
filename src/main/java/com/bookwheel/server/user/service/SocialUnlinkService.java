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

    public void unlink(SocialType socialType, String socialId) {
        if (socialType == SocialType.KAKAO) {
            unlinkKakao(socialId);
        }
        // 구글은 사용자가 직접 연동 해제 (https://myaccount.google.com/permissions)
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
}