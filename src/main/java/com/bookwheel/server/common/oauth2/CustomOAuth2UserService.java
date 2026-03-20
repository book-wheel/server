package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.oauth2.userinfo.GoogleOAuth2UserInfo;
import com.bookwheel.server.common.oauth2.userinfo.KakaoOAuth2UserInfo;
import com.bookwheel.server.common.oauth2.userinfo.OAuth2UserInfo;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("CustomOAuth2UserService.loadUser() 실행 - 소셜 로그인 시도");

        // 기본 유저 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialType socialType = getSocialType(registrationId);

        // 각 서비스에 맞는 유저 정보 추출기로 변환
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuth2UserInfo userInfo = getOAuth2UserInfo(socialType, attributes);

        // 유저 저장 및 정보 업데이트
        User user = getUser(userInfo, socialType);

        // 커스텀 유저 객체 반환
        return new CustomOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().getKey())),
                attributes,
                userNameAttributeName,
                user.getId(),
                user.getRole(),
                user.getNickname()
        );
    }

    private SocialType getSocialType(String registrationId) {
        if ("kakao".equals(registrationId)) return SocialType.KAKAO;
        return SocialType.GOOGLE;
    }

    private OAuth2UserInfo getOAuth2UserInfo(SocialType socialType, Map<String, Object> attributes) {
        if (socialType == SocialType.KAKAO) return new KakaoOAuth2UserInfo(attributes);
        return new GoogleOAuth2UserInfo(attributes);
    }

    private User getUser(OAuth2UserInfo userInfo, SocialType socialType) {
        // 소셜 타입과 소셜 고유 ID로 이미 가입된 유저인지 확인
        User findUser = userRepository.findBySocialTypeAndSocialId(socialType, userInfo.getSocialId())
                .orElse(null);

        // 신규 유저라면 바로 저장
        if (findUser == null) {
            return saveUser(userInfo, socialType);
        }

        // 탈퇴했었던 유저 처리
        if (!findUser.getIsActive()) {
            log.info("탈퇴했던 소셜 유저의 재접속: 기존 데이터를 삭제하고 신규 가입 처리합니다. userId={}", findUser.getUserId());

            // 기존 데이터 삭제 (Hard Delete)
            userRepository.delete(findUser);

            // 즉시 DB에 반영하여 중복 제약 조건 충돌 방지
            userRepository.flush();

            // 새로운 유저 엔티티 생성 및 저장
            return saveUser(userInfo, socialType);
        }

        // 정상 활동 중인 유저라면 그대로 반환
        return findUser;
    }

    private User saveUser(OAuth2UserInfo userInfo, SocialType socialType) {
        // 이메일이 있으면 이메일, 없으면 소셜 고유 ID를 userId로 사용
        String tempNickname = "USER_" + UUID.randomUUID().toString().substring(0, 8);

        String uniqueUserId = socialType.name() + "_" + userInfo.getSocialId();

        User user = User.builder()
                .userId(uniqueUserId)
                .password(UUID.randomUUID().toString())
                .socialType(socialType)
                .socialId(userInfo.getSocialId())
                .mail(userInfo.getEmail())
                .nickname(tempNickname)
                .profileImage(userInfo.getProfileImage())
                .role(Role.USER)
                .build();

        return userRepository.save(user);
    }
}
