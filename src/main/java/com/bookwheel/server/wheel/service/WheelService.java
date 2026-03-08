package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.*;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.dto.*;
import com.bookwheel.server.wheel.entity.*;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class WheelService {
    private final WheelStateRepository wheelStateRepository;
    private final MemberRepository memberRepository;
    private final RoundRepository roundRepository;
    private final OwnBookRepository ownBookRepository;


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

    @Transactional(readOnly = true)
    public List<WheelHistoryUserResponse> historyReading(String userId, String targetUserId, String groupId) {

        // 1. 소속 권한 확인
        validateGroupAccess(userId, targetUserId, groupId);

        // 2. roundId -> roundNumber
        Map<String, Integer> roundNumberMap = getRoundNumberMap(groupId);

        // 3. 해당 멤버의 기록 중 COMPLETED 상태인 WheelState 리스트 가져오기
        List<WheelState> wheelStates = wheelStateRepository.findMyCompletedHistories(groupId, targetUserId, WheelStatus.COMPLETED);

        // 4. WheelState 리스트를 WheelHistoryUserResponse 리스트로 변환하기
        return wheelStates.stream()
                // 라운드 데이터가 존재하는 데이터만 사용
                .filter(wheelState -> roundNumberMap.containsKey(wheelState.getRoundId()))
                .map(wheelState -> {
                    int realRoundNumber = roundNumberMap.get(wheelState.getRoundId());
                    return WheelHistoryUserResponse.of(wheelState, realRoundNumber);
                })
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public WheelHistoryBookResponse historyReadingBook(String userId, String groupId, String ownBookId) {
        // 권한 확인 (내가 그룹원이면 되기 때문에 userId 두 번 삽입)
        validateGroupAccess(userId, userId, groupId);
        Map<String, Integer> roundNumberMap = getRoundNumberMap(groupId);

        // 책이 존재하지 않으면 오류
        OwnBook ownBook = ownBookRepository.findById(ownBookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        // 특정 책의 완독 기록 가져오기
        List<WheelState> wheelStates = wheelStateRepository.findAllByOwnBookIdWithMemberAndImages(groupId, ownBookId, WheelStatus.COMPLETED);

        // 만약 기록이 하나도 없다면 빈 리스트 반환
        if (wheelStates.isEmpty()) {
            return WheelHistoryBookResponse.of(ownBook, Collections.emptyList());
        }

        // 기록이 있다면 데이터 조립
        List<HistoryDto> histories = wheelStates.stream()
                .map(ws -> HistoryDto.of(ws, roundNumberMap.getOrDefault(ws.getRoundId(), 0)))
                .toList();

        return WheelHistoryBookResponse.of(wheelStates.get(0).getOwnBook(), histories);
    }

    private void validateGroupAccess(String userId, String targetId, String groupId) {
        boolean isRequesterMember = memberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, userId);
        boolean isTargetMember = memberRepository.existsByGroup_GroupIdAndUser_UserId(groupId, targetId);

        if (!isRequesterMember || !isTargetMember) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    // 🗺️ 2. 바코드 번역기 생성 도우미 (Round Map)
    private Map<String, Integer> getRoundNumberMap(String groupId) {
        return roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)
                .stream()
                .collect(Collectors.toMap(Round::getRoundId, Round::getRoundNumber));
    }
}
