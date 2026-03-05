package com.bookwheel.server.wheel.service;

import com.bookwheel.server.common.exception.*;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.dto.*;
import com.bookwheel.server.wheel.entity.*;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class WheelService {
    private final WheelStateRepository wheelStateRepository;


    @Transactional
    public WheelCompleteResponse completedReading(String userId, String wheelStateId, WheelCompleteRequest request) {
        // 1. DB에서 해당 WheelState가 있는지 먼저 찾기
        WheelState wheelState = wheelStateRepository.findById(wheelStateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WHEEL_NOT_FOUND));

        // 2. 이 사람이 인증할 권한이 있는 사람인지 확인
        if (!wheelState.getMember().getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }

        // TODO: 북 게시판 사진첩 연동 기능 추가 예정 (BookService 연동)

        //3. DB에 데이터 저장하기
        wheelState.complete(request.reviewText(), request.imageUrls());

        return WheelCompleteResponse.of(wheelState.getWheelStateId(), wheelState.getIsCompleted(), wheelState.getWheelState());

    }
}
