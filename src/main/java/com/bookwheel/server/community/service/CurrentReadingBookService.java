package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.CurrentReadingBookResponse;
import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentReadingBookService {

    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    // 진행 중인 책을 먼저, 시작 예정인 첫 라운드의 PLANNED 배정 책을 그다음에 내려준다.
    public CurrentReadingBooksResponse getCurrentReadingBooks(String userPK) {
        LocalDate today = LocalDate.now(clock);
        return new CurrentReadingBooksResponse(
                Stream.concat(
                        wheelStateRepository.findCurrentReadingBooks(
                                        userPK,
                                        today,
                                        MemberStatus.ACTIVE,
                                        State.IN_PROGRESS
                                )
                                .stream()
                                .map(assignment -> CurrentReadingBookResponse.reading(
                                        assignment.getGroupId(),
                                        assignment.getTitle(),
                                        assignment.getCoverImageUrl(),
                                        assignment.getRoundStartDate()
                                )),
                        wheelStateRepository.findUpcomingReadingBooks(
                                        userPK,
                                        today,
                                        MemberStatus.ACTIVE,
                                        State.RECRUITING,
                                        WheelStatus.PLANNED
                                )
                                .stream()
                                .map(assignment -> CurrentReadingBookResponse.upcoming(
                                        assignment.getGroupId(),
                                        assignment.getTitle(),
                                        assignment.getCoverImageUrl(),
                                        assignment.getRoundStartDate(),
                                        today
                                ))
                ).toList()
        );
    }
}
