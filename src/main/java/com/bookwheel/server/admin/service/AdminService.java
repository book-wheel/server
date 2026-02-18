package com.bookwheel.server.admin.service;


import com.bookwheel.server.admin.dto.AdminBanRequest;
import com.bookwheel.server.admin.dto.AdminBanResponse;
import com.bookwheel.server.admin.dto.BanReason;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {
    // TODO: 신고 목록 조회, 패널티 목록 조회 등의 로직 처리 할 예정

    private final UserRepository userRepository;

    //회원 강제 탈퇴/정지 시키기
    @Transactional
    public AdminBanResponse banUser(String userId, AdminBanRequest request) {

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ALREADY_BANNED_USER);
        }

        user.applyBan(request.banType());



        String reasonMessage = (request.reasonCode() == BanReason.ETC)
            ? request.detailedReason()
            : request.reasonCode().getDescription();

        return AdminBanResponse.builder()
            .userId(user.getUserId())
            .nickname(user.getNickname())
            .status(user.getIsActive() ? "BANNED" : "PERMANENT_BANNED")
            .banType(request.banType())
            .reasonMessage(reasonMessage)
            .bannedAt(LocalDateTime.now())
            .releaseDate(user.getBanExpiredAt()) // 아직 엔티티에 날짜 필드가 없으므로 임시로 null 처리
            .build();
    }
}



