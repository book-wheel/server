package com.bookwheel.server.user.service;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.bookwheel.server.user.dto.LoginResponse;
import com.bookwheel.server.user.dto.ProfileImagePresignedUrlRequest;
import com.bookwheel.server.user.dto.ProfileImagePresignedUrlResponse;
import com.bookwheel.server.user.dto.ProfileSetupRequest;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileSetupTest {

    private static final String USER_PK = "user-pk";
    private static final String OTHER_USER_PK = "other-user-pk";
    private static final String UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEMPORARY_OBJECT_KEY = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".png";
    private static final String CURRENT_OBJECT_KEY = "profiles/legacy-current.png";
    private static final String ETAG = "\"profile-etag\"";
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private SocialUnlinkService socialUnlinkService;
    @Mock
    private S3Service s3Service;
    @Mock
    private ProfileSetupTransactionService profileSetupTransactionService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("프로필 전용 Presigned URL은 userPK 귀속 임시 key와 용량·MIME 조건을 사용한다")
    void createProfileImagePresignedUrl_ReturnsOwnedTemporaryKey() {
        givenActiveUser();
        given(s3Service.getPresignedPutUrl(anyString(), eq("image/png"), eq(123_456L)))
                .willReturn("https://s3.example.com/upload");

        ProfileImagePresignedUrlResponse response = userService.createProfileImagePresignedUrl(
                USER_PK,
                new ProfileImagePresignedUrlRequest("profile.PNG", "image/png", 123_456L)
        );

        assertThat(response.presignedUrl()).isEqualTo("https://s3.example.com/upload");
        assertThat(response.objectKey()).startsWith("profiles-temp/" + USER_PK + "/").endsWith(".png");
        assertThat(response.contentType()).isEqualTo("image/png");
        then(s3Service).should().getPresignedPutUrl(response.objectKey(), "image/png", 123_456L);
    }

    @Test
    @DisplayName("profileImageKey 누락은 S3 처리 없이 DB 트랜잭션에 유지 의미로 전달한다")
    void setupProfile_MissingImageKey_RetainsWithoutS3Work() {
        ProfileSetupRequest request = new ProfileSetupRequest(null, "comment", null);
        LoginResponse response = loginResponse(CURRENT_OBJECT_KEY);
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willReturn(new ProfileSetupTransactionService.Result(response, CURRENT_OBJECT_KEY));
        ArgumentCaptor<ProfileSetupTransactionService.ProfileImageUpdate> captor =
                ArgumentCaptor.forClass(ProfileSetupTransactionService.ProfileImageUpdate.class);

        LoginResponse result = userService.setupProfile(USER_PK, request);

        assertThat(result).isSameAs(response);
        then(profileSetupTransactionService).should().persist(eq(USER_PK), eq(request), captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(ProfileSetupTransactionService.ProfileImageUpdate.Type.RETAIN);
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("The current final key is delegated to the transaction for a locked equality check")
    void setupProfile_StoredImageKey_RequiresCurrentKeyMatch() {
        ProfileSetupRequest request = new ProfileSetupRequest(CURRENT_OBJECT_KEY, "comment", null);
        LoginResponse response = loginResponse(CURRENT_OBJECT_KEY);
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willReturn(new ProfileSetupTransactionService.Result(response, CURRENT_OBJECT_KEY));
        ArgumentCaptor<ProfileSetupTransactionService.ProfileImageUpdate> captor =
                ArgumentCaptor.forClass(ProfileSetupTransactionService.ProfileImageUpdate.class);

        LoginResponse result = userService.setupProfile(USER_PK, request);

        assertThat(result).isSameAs(response);
        then(profileSetupTransactionService).should().persist(eq(USER_PK), eq(request), captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(ProfileSetupTransactionService.ProfileImageUpdate.Type.RETAIN_IF_CURRENT);
        assertThat(captor.getValue().finalObjectKey()).isEqualTo(CURRENT_OBJECT_KEY);
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("빈 profileImageKey로 DB 삭제가 커밋되면 더 이상 참조되지 않는 기존 S3 객체를 삭제한다")
    void setupProfile_BlankImageKey_DeletesUnreferencedPreviousObject() {
        ProfileSetupRequest request = new ProfileSetupRequest("   ", "comment", null);
        LoginResponse response = loginResponse(null);
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willReturn(new ProfileSetupTransactionService.Result(response, CURRENT_OBJECT_KEY));
        given(userRepository.existsByProfileImageKey(CURRENT_OBJECT_KEY)).willReturn(false);
        given(s3Service.deleteObject(CURRENT_OBJECT_KEY)).willReturn(true);
        ArgumentCaptor<ProfileSetupTransactionService.ProfileImageUpdate> captor =
                ArgumentCaptor.forClass(ProfileSetupTransactionService.ProfileImageUpdate.class);

        userService.setupProfile(USER_PK, request);

        then(profileSetupTransactionService).should().persist(eq(USER_PK), eq(request), captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(ProfileSetupTransactionService.ProfileImageUpdate.Type.DELETE);
        then(s3Service).should().deleteObject(CURRENT_OBJECT_KEY);
    }

    @Test
    @DisplayName("소유·메타데이터·시그니처를 검증한 임시 이미지만 최종 key로 확정한다")
    void setupProfile_OwnedValidTemporaryImage_PromotesAndPersists() {
        ProfileSetupRequest request = new ProfileSetupRequest(TEMPORARY_OBJECT_KEY, "comment", null);
        givenValidUploadedObject();
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willAnswer(invocation -> {
                    ProfileSetupTransactionService.ProfileImageUpdate update = invocation.getArgument(2);
                    return new ProfileSetupTransactionService.Result(
                            loginResponse(update.finalObjectKey()),
                            CURRENT_OBJECT_KEY
                    );
                });
        given(userRepository.existsByProfileImageKey(CURRENT_OBJECT_KEY)).willReturn(false);
        given(s3Service.deleteObject(TEMPORARY_OBJECT_KEY)).willReturn(true);
        given(s3Service.deleteObject(CURRENT_OBJECT_KEY)).willReturn(true);
        ArgumentCaptor<ProfileSetupTransactionService.ProfileImageUpdate> captor =
                ArgumentCaptor.forClass(ProfileSetupTransactionService.ProfileImageUpdate.class);
        ArgumentCaptor<String> finalKeyCaptor = ArgumentCaptor.forClass(String.class);

        LoginResponse result = userService.setupProfile(USER_PK, request);

        InOrder order = inOrder(s3Service, profileSetupTransactionService);
        order.verify(s3Service).getObjectMetadata(TEMPORARY_OBJECT_KEY);
        order.verify(s3Service).getObjectSignature(TEMPORARY_OBJECT_KEY, ETAG, 12);
        order.verify(s3Service).copyObjectIfUnchanged(
                eq(TEMPORARY_OBJECT_KEY),
                finalKeyCaptor.capture(),
                eq(ETAG)
        );
        order.verify(profileSetupTransactionService).persist(eq(USER_PK), eq(request), captor.capture());
        String promotedObjectKey = finalKeyCaptor.getValue();
        assertThat(promotedObjectKey).matches("profiles/" + USER_PK + "/[0-9a-f-]{36}\\.png");
        assertThat(result.profileImageKey()).isEqualTo(promotedObjectKey);
        assertThat(captor.getValue().type())
                .isEqualTo(ProfileSetupTransactionService.ProfileImageUpdate.Type.REPLACE);
        assertThat(captor.getValue().finalObjectKey()).isEqualTo(promotedObjectKey);
        then(s3Service).should().deleteObject(TEMPORARY_OBJECT_KEY);
        then(s3Service).should().deleteObject(CURRENT_OBJECT_KEY);
        then(s3Service).should(never()).deleteObject(promotedObjectKey);
    }

    @Test
    @DisplayName("타 사용자에게 귀속된 임시 key는 참조하거나 삭제하지 않는다")
    void setupProfile_OtherUsersTemporaryKey_RejectsWithoutDeleting() {
        String otherUsersKey = "profiles-temp/" + OTHER_USER_PK + "/" + UUID_VALUE + ".png";

        assertThatThrownBy(() -> userService.setupProfile(
                USER_PK,
                new ProfileSetupRequest(otherUsersKey, "comment", null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_KEY);

        then(s3Service).shouldHaveNoInteractions();
        then(profileSetupTransactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("임시 객체가 실제 이미지가 아니면 정리하고 DB에 저장하지 않는다")
    void setupProfile_InvalidUploadedObject_DeletesTemporaryObject() {
        given(s3Service.getObjectMetadata(TEMPORARY_OBJECT_KEY))
                .willReturn(new S3ObjectMetadata(123_456L, "image/png", ETAG));
        given(s3Service.getObjectSignature(TEMPORARY_OBJECT_KEY, ETAG, 12))
                .willReturn(new byte[12]);
        given(s3Service.deleteObject(TEMPORARY_OBJECT_KEY)).willReturn(true);

        assertThatThrownBy(() -> userService.setupProfile(
                USER_PK,
                new ProfileSetupRequest(TEMPORARY_OBJECT_KEY, "comment", null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        then(s3Service).should().deleteObject(TEMPORARY_OBJECT_KEY);
        then(s3Service).should(never()).copyObjectIfUnchanged(anyString(), anyString(), anyString());
        then(profileSetupTransactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("DB 트랜잭션이 실패하면 임시 객체와 복사를 시도한 최종 객체를 보상 삭제한다")
    void setupProfile_DatabaseFailure_DeletesTemporaryAndFinalObjects() {
        ProfileSetupRequest request = new ProfileSetupRequest(TEMPORARY_OBJECT_KEY, "comment", "duplicate");
        BusinessException originalException = new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        givenValidUploadedObject();
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willThrow(originalException);
        // 보상 삭제 실패 여부와 관계없이 원래 DB 예외가 유지되어야 한다.
        given(s3Service.deleteObject(TEMPORARY_OBJECT_KEY)).willReturn(false);
        ArgumentCaptor<String> finalKeyCaptor = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> userService.setupProfile(USER_PK, request))
                .isSameAs(originalException);

        then(s3Service).should().copyObjectIfUnchanged(
                eq(TEMPORARY_OBJECT_KEY),
                finalKeyCaptor.capture(),
                eq(ETAG)
        );
        String attemptedFinalObjectKey = finalKeyCaptor.getValue();
        assertThat(attemptedFinalObjectKey).matches("profiles/" + USER_PK + "/[0-9a-f-]{36}\\.png");
        then(s3Service).should().deleteObject(TEMPORARY_OBJECT_KEY);
        then(s3Service).should().deleteObject(attemptedFinalObjectKey);
        then(userRepository).should(never()).existsByProfileImageKey(anyString());
    }

    @Test
    @DisplayName("기존 key가 다른 계정에서도 참조 중이면 성공 후에도 삭제하지 않는다")
    void setupProfile_SharedLegacyKey_DoesNotDeletePreviousObject() {
        ProfileSetupRequest request = new ProfileSetupRequest("", "comment", null);
        given(profileSetupTransactionService.persist(eq(USER_PK), eq(request), any()))
                .willReturn(new ProfileSetupTransactionService.Result(loginResponse(null), CURRENT_OBJECT_KEY));
        given(userRepository.existsByProfileImageKey(CURRENT_OBJECT_KEY)).willReturn(true);

        userService.setupProfile(USER_PK, request);

        then(s3Service).should(never()).deleteObject(CURRENT_OBJECT_KEY);
    }

    private void givenValidUploadedObject() {
        given(s3Service.getObjectMetadata(TEMPORARY_OBJECT_KEY))
                .willReturn(new S3ObjectMetadata(123_456L, "image/png", ETAG));
        given(s3Service.getObjectSignature(TEMPORARY_OBJECT_KEY, ETAG, 12))
                .willReturn(PNG_SIGNATURE);
    }

    private void givenActiveUser() {
        User activeUser = User.builder()
                .loginId("login")
                .password("encoded-password")
                .nickname("nickname")
                .mail("user@example.com")
                .socialType(SocialType.NONE)
                .isActive(true)
                .build();
        given(userRepository.findById(USER_PK)).willReturn(Optional.of(activeUser));
    }

    private LoginResponse loginResponse(String profileImageKey) {
        return LoginResponse.builder()
                .userPK(USER_PK)
                .profileImageKey(profileImageKey)
                .build();
    }
}
