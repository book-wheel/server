package com.bookwheel.server.admin.service;


import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {
    // TODO: 신고 목록 조회, 패널티 목록 조회 등의 로직 처리 할 예정

    private final UserRepository userRepository;

    //회원 강제 탈퇴/정지 시키기
    @Transactional
    public void banUser(String userId){

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN);
        }

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.ALREADY_BANNED_USER);
        }

        user.deactivate();
        // TODO: 만약 영구 정지 뿐만이 아닌 ３일정지 ７일정지 엔티티 추가 된다 하면 바로 개발



    }
}
