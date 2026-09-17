package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.oauth2.userinfo.OAuth2UserInfo;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OAuth2UserInfo userInfo;

    @InjectMocks
    private CustomOAuth2UserService userService;

    @Test
    @DisplayName("소셜 신규 회원의 외부 프로필 URL을 S3 objectKey 컬럼에 저장하지 않는다")
    void saveUser_DoesNotStoreExternalProfileImageUrl() {
        given(userInfo.getSocialId()).willReturn("google-social-id");
        given(userInfo.getEmail()).willReturn("social@example.com");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        User savedUser = ReflectionTestUtils.invokeMethod(
                userService,
                "saveUser",
                userInfo,
                SocialType.GOOGLE
        );

        then(userRepository).should().save(captor.capture());
        assertThat(savedUser).isSameAs(captor.getValue());
        assertThat(savedUser.getProfileImageKey()).isNull();
    }
}
