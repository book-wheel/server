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
        switch (socialType) {
            case KAKAO -> unlinkKakao(socialId);
            case APPLE -> log.warn(
                    "Apple provider token을 보관하지 않아 연동 해제를 건너뜁니다. "
                            + "회원 탈퇴 처리는 계속합니다."
            );
            case GOOGLE -> {
                // Google 연동 해제는 사용자가 Google 계정의 연결 관리에서 직접 처리한다.
            }
            case NONE -> {
                // 일반 회원은 해제할 소셜 연결이 없다.
            }
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
}
