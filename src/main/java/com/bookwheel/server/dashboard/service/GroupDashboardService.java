package com.bookwheel.server.dashboard.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.dashboard.dto.DashboardResponse;
import com.bookwheel.server.dashboard.dto.MyBookStepResponse;
import com.bookwheel.server.dashboard.dto.MyStepResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.Repository.RoundRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupDashboardService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OwnBookRepository ownBookRepository;
    private final WheelStateRepository wheelStateRepository;
    private final RoundRepository roundRepository;

    public DashboardResponse getDashboard(String groupId, String userId) {
        // 1. 유저, 그룹, 멤버 권한 체크
        User user = findActiveUserByUserId(userId);
        Group group = findGroupById(groupId);
        Member member = findActiveMember(groupId, userId);

        // 2. 현재 라운드 조회
        Round currentRound = getCurrentRound(groupId);

        // 아직 라운드 일정이 생성되지 않았거나 시작되지 않은 경우
        if (currentRound == null) {
            LocalDate plannedStartDate = group.getStartDate();
            int dDay = 0;

            // 그룹의 예비 시작 날짜가 설정되어 있다면 시작일까지 남은 D-Day 계산
            if (plannedStartDate != null) {
                dDay = (int) ChronoUnit.DAYS.between(LocalDate.now(), plannedStartDate);
            }

            return DashboardResponse.of(
                    group.getGroupName(),
                    0,                              // currentRound (아직 0회차)
                    group.getGroupRoundCount(),     // totalRound (총 회차 수)
                    plannedStartDate,               // startDate (예비 시작 날짜 반환)
                    null,                           // endDate (아직 정확한 일정이 없으므로 null)
                    dDay,                           // dDay (시작일까지 남은 일수)
                    null,                           // myStep (해당 라운드 책 없음)
                    null                            // myBookStep (내 책 전달 정보 없음)
            );
        }

        // 3. dDay 계산
        int dDay = (int) ChronoUnit.DAYS.between(LocalDate.now(), currentRound.getEndDate());

        // 4. myStep (내가 현재 읽고 있는 책 상태 조회)
        MyStepResponse myStep = null;
        Optional<WheelState> myWheelStateOpt = wheelStateRepository
                .findByRoundIdAndMember_MemberId(currentRound.getRoundId(), member.getMemberId());

        // 책 정보가 존재한다면 그것을 토대로 반환
        if (myWheelStateOpt.isPresent()) {
            WheelState ws = myWheelStateOpt.get();
            Book book = ws.getOwnBook().getBook();

            String previousSenderName = resolvePreviousSenderName(groupId, currentRound, ws);

            myStep = MyStepResponse.of(
                    ws.getWheelStateId(),
                    book.getBookId(),
                    WheelStatus.valueOf(ws.getWheelState().name()),
                    book.getTitle(),
                    book.getCoverImage(),
                    previousSenderName
            );
        }

        // 5. myBookStep (내 책이 현재 어디에 있는지 조회)
        MyBookStepResponse myBookStep = null;
        Optional<OwnBook> myOwnBookOpt = ownBookRepository.findByGroup_GroupIdAndOwner_Id(groupId, user.getId());

        // 내 책이 존재하면 정보 가져오기
        if (myOwnBookOpt.isPresent()) {
            OwnBook myOwnBook = myOwnBookOpt.get();
            Optional<WheelState> bookWheelStateOpt = wheelStateRepository
                    .findFirstByRoundIdAndOwnBook_OwnbookId(currentRound.getRoundId(), myOwnBook.getOwnbookId());

            if (bookWheelStateOpt.isPresent()) {
                WheelState ws = bookWheelStateOpt.get();
                myBookStep = MyBookStepResponse.of(
                        myOwnBook.getBook().getBookId(),
                        myOwnBook.getBook().getTitle(),
                        ws.getMember().getUser().getNickname(),
                        ws.getWheelState(),
                        group.isGroupOffline() ? group.getGroupRegion().getDescription() : null
                );
            }
        }

        // 6. 결과 반환
        return DashboardResponse.of(
                group.getGroupName(),
                currentRound.getRoundNumber(),
                group.getGroupRoundCount(),
                currentRound.getStartDate(),
                currentRound.getEndDate(),
                dDay,
                myStep,
                myBookStep
        );
    }

    // 이전 전달자 닉네임 계산:
    // 1라운드면 원래 책 주인, 2라운드 이상이면 이전 라운드의 같은 책 소유자
    private String resolvePreviousSenderName(String groupId, Round currentRound, WheelState currentWheelState) {
        if (currentRound.getRoundNumber() == 1) {
            return currentWheelState.getOwnBook().getOwner().getNickname();
        }

        Integer previousRoundNumber = currentRound.getRoundNumber() - 1;

        return roundRepository
                .findByGroup_GroupIdAndRoundNumber(groupId, previousRoundNumber)
                .flatMap(previousRound -> wheelStateRepository.findFirstByRoundIdAndOwnBook_OwnbookId(
                        previousRound.getRoundId(),
                        currentWheelState.getOwnBook().getOwnbookId()
                ))
                .map(previousWheelState -> previousWheelState.getMember().getUser().getNickname())
                .orElse(null);
    }

    private Round getCurrentRound(String groupId) {
        LocalDate today = LocalDate.now();

        // 라운드 전체를 회차 오름차순으로 조회
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);

        if (rounds.isEmpty()) {
            return null;
        }

        // 1. 오늘 날짜가 포함된 진행 중 라운드 찾기
        for (Round round : rounds) {
            if (!today.isBefore(round.getStartDate()) && !today.isAfter(round.getEndDate())) {
                return round;
            }
        }

        // 아직 첫 라운드 시작 전이면 null 반환
        Round firstRound = rounds.get(0);
        if (firstRound.getStartDate() != null && today.isBefore(firstRound.getStartDate())) {
            return null;
        }

        // 3. 모두 종료되었다면 마지막 라운드 반환
        return rounds.get(rounds.size() - 1);
    }

    private User findActiveUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }
        return user;
    }

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Member findActiveMember(String groupId, String userId) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_UserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }
        return member;
    }
}