package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentReadingBookService {

    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    // 활동 중인 멤버가 진행 중인 그룹에서 이번 라운드에 읽는 책만 카드로 내려준다.
    public CurrentReadingBooksResponse getCurrentReadingBooks(String userPK) {
        LocalDate today = LocalDate.now(clock);
        return new CurrentReadingBooksResponse(
            wheelStateRepository.findCurrentReadingBooks(userPK, today, MemberStatus.ACTIVE, State.IN_PROGRESS)
        );
    }
}
