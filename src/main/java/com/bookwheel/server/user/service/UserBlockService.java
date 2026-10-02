package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.dto.UserBlockResponse;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.entity.UserBlock;
import com.bookwheel.server.user.repository.UserBlockRepository;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final S3Service s3Service;

    @Transactional
    public void block(String blockerUserPK, String blockedUserPK) {
        if (blockerUserPK.equals(blockedUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        boolean blockerFirst = blockerUserPK.compareTo(blockedUserPK) < 0;
        User first = lockUser(blockerFirst ? blockerUserPK : blockedUserPK);
        User second = lockUser(blockerFirst ? blockedUserPK : blockerUserPK);
        User blocker = blockerFirst ? first : second;
        User blocked = blockerFirst ? second : first;
        requireActive(blocker);
        if (!Boolean.TRUE.equals(blocked.getIsActive())) {
            throw new BusinessException(ErrorCode.BLOCK_TARGET_INACTIVE);
        }

        if (!userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerUserPK, blockedUserPK)) {
            userBlockRepository.save(UserBlock.create(blocker, blocked));
        }
    }

    @Transactional
    public void unblock(String blockerUserPK, String blockedUserPK) {
        requireActive(lockUser(blockerUserPK));
        userBlockRepository.deleteBlock(blockerUserPK, blockedUserPK);
    }

    public Page<UserBlockResponse> getBlocks(String userPK, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireActive(userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));

        return userBlockRepository.findAllByBlocker_IdOrderByCreatedAtDescBlockIdDesc(
            userPK, PageRequest.of(page, size)
        ).map(block -> {
            User blocked = block.getBlocked();
            boolean active = Boolean.TRUE.equals(blocked.getIsActive());
            String profileImageUrl = active && StringUtils.hasText(blocked.getProfileImageKey())
                ? s3Service.getPresignedGetUrl(blocked.getProfileImageKey()) : null;
            return new UserBlockResponse(blocked.getId(),
                active ? blocked.getNickname() : "탈퇴한 사용자", profileImageUrl, block.getCreatedAt());
        });
    }

    private User lockUser(String userPK) {
        return userRepository.findByUserPKForUpdate(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void requireActive(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }
    }
}
