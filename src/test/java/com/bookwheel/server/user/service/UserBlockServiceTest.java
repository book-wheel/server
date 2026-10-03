package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.entity.UserBlock;
import com.bookwheel.server.user.repository.UserBlockRepository;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserBlockRepository userBlockRepository;
    @Mock S3Service s3Service;
    @InjectMocks UserBlockService service;

    private User user(String userPK, boolean active) {
        User user = User.builder().nickname("nickname").isActive(active).profileImageKey("profiles/example.png").build();
        ReflectionTestUtils.setField(user, "id", userPK);
        return user;
    }

    private void locks(User... users) {
        for (User user : users) {
            when(userRepository.findByUserPKForUpdate(user.getId())).thenReturn(Optional.of(user));
        }
    }

    @Test void blockStoresDirectedRelationshipInStableLockOrder() {
        User blocker = user("b", true);
        User blocked = user("a", true);
        locks(blocker, blocked);
        service.block("b", "a");
        var order = inOrder(userRepository, userBlockRepository);
        order.verify(userRepository).findByUserPKForUpdate("a");
        order.verify(userRepository).findByUserPKForUpdate("b");
        order.verify(userBlockRepository).existsByBlocker_IdAndBlocked_Id("b", "a");
        order.verify(userBlockRepository).save(argThat(block -> block.getBlocker() == blocker && block.getBlocked() == blocked));
    }

    @Test void repeatedBlockDoesNotInsert() {
        locks(user("a", true), user("b", true));
        when(userBlockRepository.existsByBlocker_IdAndBlocked_Id("a", "b")).thenReturn(true);
        service.block("a", "b");
        verify(userBlockRepository, never()).save(any());
    }

    @Test void selfBlockIsRejected() {
        assertError(() -> service.block("a", "a"), ErrorCode.CANNOT_BLOCK_SELF);
        verifyNoInteractions(userRepository, userBlockRepository);
    }

    @Test void missingTargetIsRejected() {
        locks(user("a", true));
        assertError(() -> service.block("a", "b"), ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(userBlockRepository);
    }

    @Test void inactiveTargetIsRejected() {
        locks(user("a", true), user("b", false));
        assertError(() -> service.block("a", "b"), ErrorCode.BLOCK_TARGET_INACTIVE);
        verifyNoInteractions(userBlockRepository);
    }

    @Test void inactiveRequesterCannotBlock() {
        locks(user("a", false), user("b", true));
        assertError(() -> service.block("a", "b"), ErrorCode.INACTIVE_USER);
        verifyNoInteractions(userBlockRepository);
    }

    @Test void unblockDoesNotRequireExistingOrActiveTarget() {
        locks(user("a", true));
        service.unblock("a", "deleted-target");
        service.unblock("a", "deleted-target");
        verify(userBlockRepository, times(2)).deleteBlock("a", "deleted-target");
        verify(userRepository, never()).findByUserPKForUpdate("deleted-target");
    }

    @Test void inactiveRequesterCannotUnblock() {
        locks(user("a", false));
        assertError(() -> service.unblock("a", "b"), ErrorCode.INACTIVE_USER);
        verifyNoInteractions(userBlockRepository);
    }

    @Test void listUsesOnlyRequesterAndMasksWithdrawnTargets() {
        User owner = user("owner", true);
        User active = user("active", true);
        User withdrawn = user("withdrawn", false);
        UserBlock first = UserBlock.create(owner, active);
        UserBlock second = UserBlock.create(owner, withdrawn);
        LocalDateTime blockedAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        ReflectionTestUtils.setField(first, "createdAt", blockedAt);
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner));
        PageRequest page = PageRequest.of(0, 20);
        when(userBlockRepository.findAllByBlocker_IdOrderByCreatedAtDescBlockIdDesc("owner", page))
            .thenReturn(new PageImpl<>(List.of(first, second), page, 2));
        when(s3Service.getPresignedGetUrl("profiles/example.png")).thenReturn("https://example.com/profile");

        var response = service.getBlocks("owner", 0, 20);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent().get(0).userPK()).isEqualTo("active");
        assertThat(response.getContent().get(0).profileImageUrl()).isEqualTo("https://example.com/profile");
        assertThat(response.getContent().get(0).blockedAt()).isEqualTo(blockedAt);
        assertThat(response.getContent().get(1).userPK()).isEqualTo("withdrawn");
        assertThat(response.getContent().get(1).nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(response.getContent().get(1).profileImageUrl()).isNull();
        verify(s3Service).getPresignedGetUrl(anyString());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,51"})
    void invalidPaginationIsRejected(int page, int size) {
        assertError(() -> service.getBlocks("a", page, size), ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(userRepository, userBlockRepository);
    }

    @Test void inactiveRequesterCannotList() {
        when(userRepository.findById("a")).thenReturn(Optional.of(user("a", false)));
        assertError(() -> service.getBlocks("a", 0, 20), ErrorCode.INACTIVE_USER);
        verifyNoInteractions(userBlockRepository);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
