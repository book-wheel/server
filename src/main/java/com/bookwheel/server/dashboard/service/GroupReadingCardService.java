package com.bookwheel.server.dashboard.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.dashboard.dto.GroupReadingCardResponse;
import com.bookwheel.server.dashboard.dto.MyBookStepResponse;
import com.bookwheel.server.dashboard.dto.MyStepResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupReadingCardService {
    private static final String STATUS_SCHEDULED = "scheduled";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_RESCHEDULE_REQUIRED = "reschedule_required";

    private final MemberRepository memberRepository;
    private final OwnBookRepository ownBookRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    public List<GroupReadingCardResponse> getReadingCards(String userPK) {
        // 배정이 없는 모임도 카드에 남겨야 하므로 ACTIVE 멤버십에서 조회를 시작
        List<Member> memberships = memberRepository.findReadingCardMemberships(
                userPK,
                MemberStatus.ACTIVE,
                List.of(State.RECRUITING, State.IN_PROGRESS)
        );

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<String> groupIds = memberships.stream()
                .map(member -> member.getGroup().getGroupId())
                .toList();

        // 카드 조립에 필요한 데이터를 종류별로 일괄 조회
        Map<String, List<Round>> roundsByGroupId = roundRepository
                .findExecutableRoundsByGroupIds(groupIds)
                .stream()
                .collect(Collectors.groupingBy(
                        round -> round.getGroup().getGroupId(),
                        Collectors.toList()
                ));

        Map<String, OwnBook> ownBookByGroupId = ownBookRepository
                .findAllByOwnerIdAndGroupIdInWithBook(userPK, groupIds)
                .stream()
                .collect(Collectors.toMap(
                        ownBook -> ownBook.getGroup().getGroupId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        LocalDate today = LocalDate.now(clock);
        Map<String, RoundSelection> roundSelectionByGroupId = selectRounds(
                memberships,
                roundsByGroupId,
                today
        );
        WheelStateIndex wheelStateIndex = loadWheelStateIndex(roundSelectionByGroupId.values());

        return memberships.stream()
                .map(member -> toResponse(
                        member,
                        roundSelectionByGroupId.get(member.getGroup().getGroupId()),
                        ownBookByGroupId.get(member.getGroup().getGroupId()),
                        wheelStateIndex,
                        today
                ))
                .toList();
    }

    private Map<String, RoundSelection> selectRounds(
            List<Member> memberships,
            Map<String, List<Round>> roundsByGroupId,
            LocalDate today
    ) {
        Map<String, RoundSelection> selections = new HashMap<>();

        for (Member membership : memberships) {
            Group group = membership.getGroup();
            List<Round> rounds = roundsByGroupId.getOrDefault(group.getGroupId(), List.of());

            if (group.getGroupState() == State.RECRUITING) {
                // 재일정 전 배정이 노출되지 않도록 현재 그룹 시작일과 일치하는 첫 회차만 사용
                Round firstRound = rounds.stream()
                        .filter(round -> round.getRoundNumber() == 1)
                        .filter(round -> Objects.equals(round.getStartDate(), group.getStartDate()))
                        .findFirst()
                        .orElse(null);
                selections.put(group.getGroupId(), new RoundSelection(firstRound, null));
                continue;
            }

            Round currentRound = rounds.stream()
                    .filter(round -> round.getStartDate() != null && round.getEndDate() != null)
                    .filter(round -> !today.isBefore(round.getStartDate()) && !today.isAfter(round.getEndDate()))
                    .findFirst()
                    .orElse(null);

            // 2회차부터 senderNickname은 같은 책을 읽은 직전 회차 멤버의 닉네임
            Round previousRound = currentRound == null || currentRound.getRoundNumber() == 1
                    ? null
                    : findRound(rounds, currentRound.getRoundNumber() - 1);
            selections.put(group.getGroupId(), new RoundSelection(currentRound, previousRound));
        }

        return selections;
    }

    private Round findRound(List<Round> rounds, int roundNumber) {
        return rounds.stream()
                .filter(round -> round.getRoundNumber() == roundNumber)
                .findFirst()
                .orElse(null);
    }

    private WheelStateIndex loadWheelStateIndex(Collection<RoundSelection> selections) {
        // 현재/예정 회차와 직전 전달자 계산에 필요한 회차만 골라 전체 배정을 한 번에 읽기
        Set<String> roundIds = new LinkedHashSet<>();
        for (RoundSelection selection : selections) {
            if (selection.targetRound() != null) {
                roundIds.add(selection.targetRound().getRoundId());
            }
            if (selection.previousRound() != null) {
                roundIds.add(selection.previousRound().getRoundId());
            }
        }

        if (roundIds.isEmpty()) {
            return WheelStateIndex.empty();
        }

        return WheelStateIndex.from(
                wheelStateRepository.findAllByRoundIdInWithReadingCardDetails(roundIds)
        );
    }

    private GroupReadingCardResponse toResponse(
            Member member,
            RoundSelection selection,
            OwnBook ownBook,
            WheelStateIndex wheelStateIndex,
            LocalDate today
    ) {
        Group group = member.getGroup();
        Round targetRound = selection == null ? null : selection.targetRound();

        if (group.getGroupState() == State.RECRUITING) {
            return toRecruitingResponse(group, member, targetRound, ownBook, wheelStateIndex, today);
        }
        return toInProgressResponse(group, member, selection, ownBook, wheelStateIndex, today);
    }

    private GroupReadingCardResponse toRecruitingResponse(
            Group group,
            Member member,
            Round firstRound,
            OwnBook ownBook,
            WheelStateIndex wheelStateIndex,
            LocalDate today
    ) {
        LocalDate startDate = group.getStartDate();
        boolean rescheduleRequired = startDate != null && startDate.isBefore(today);

        // 지난 시작일을 음수 D-day로 내리지 않고 프론트가 재일정 필요 상태로 처리하게 한다.
        Integer dDay = startDate == null || rescheduleRequired
                ? null
                : daysBetween(today, startDate);

        WheelState myWheelState = firstRound == null
                ? null
                : wheelStateIndex.findByMember(firstRound.getRoundId(), member.getMemberId());
        MyStepResponse myStep = myWheelState != null && myWheelState.getWheelState() == WheelStatus.PLANNED
                ? toPreStartMyStep(myWheelState)
                : null;

        MyBookStepResponse myBookStep = toUnassignedMyBookStep(ownBook);
        if (firstRound != null && ownBook != null) {
            WheelState ownBookWheelState = wheelStateIndex.findByOwnBook(
                    firstRound.getRoundId(),
                    ownBook.getOwnBookId()
            );
            if (ownBookWheelState != null && ownBookWheelState.getWheelState() == WheelStatus.PLANNED) {
                myBookStep = toAssignedMyBookStep(ownBook, ownBookWheelState, null);
            }
        }

        return GroupReadingCardResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .status(rescheduleRequired ? STATUS_RESCHEDULE_REQUIRED : STATUS_SCHEDULED)
                .currentRound(0)
                .totalRound(group.getGroupRoundCount())
                .startDate(startDate)
                .endDate(startDate)
                .dDay(dDay)
                .myStep(myStep)
                .myBookStep(myBookStep)
                .build();
    }

    private GroupReadingCardResponse toInProgressResponse(
            Group group,
            Member member,
            RoundSelection selection,
            OwnBook ownBook,
            WheelStateIndex wheelStateIndex,
            LocalDate today
    ) {
        Round currentRound = selection == null ? null : selection.targetRound();
        Round previousRound = selection == null ? null : selection.previousRound();

        MyStepResponse myStep = null;
        MyBookStepResponse myBookStep = toUnassignedMyBookStep(ownBook);
        if (currentRound != null) {
            WheelState myWheelState = wheelStateIndex.findByMember(
                    currentRound.getRoundId(),
                    member.getMemberId()
            );
            if (myWheelState != null) {
                myStep = toCurrentMyStep(myWheelState, currentRound, previousRound, wheelStateIndex);
            }

            if (ownBook != null) {
                WheelState ownBookWheelState = wheelStateIndex.findByOwnBook(
                        currentRound.getRoundId(),
                        ownBook.getOwnBookId()
                );
                if (ownBookWheelState != null) {
                    String location = group.isGroupOffline() && group.getGroupRegion() != null
                            ? group.getGroupRegion().getDescription()
                            : null;
                    myBookStep = toAssignedMyBookStep(ownBook, ownBookWheelState, location);
                }
            }
        }

        return GroupReadingCardResponse.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .status(STATUS_ACTIVE)
                .currentRound(currentRound == null ? 0 : currentRound.getRoundNumber())
                .totalRound(group.getGroupRoundCount())
                .startDate(currentRound == null ? null : currentRound.getStartDate())
                .endDate(currentRound == null ? null : currentRound.getEndDate())
                .dDay(currentRound == null ? null : daysBetween(today, currentRound.getEndDate()))
                .myStep(myStep)
                .myBookStep(myBookStep)
                .build();
    }

    private MyStepResponse toPreStartMyStep(WheelState wheelState) {
        Book book = wheelState.getOwnBook().getBook();
        return MyStepResponse.of(
                wheelState.getWheelStateId(),
                book.getBookId(),
                wheelState.getWheelState(),
                book.getTitle(),
                book.getCoverImage(),
                wheelState.getOwnBook().getOwner().getNickname()
        );
    }

    private MyStepResponse toCurrentMyStep(
            WheelState wheelState,
            Round currentRound,
            Round previousRound,
            WheelStateIndex wheelStateIndex
    ) {
        Book book = wheelState.getOwnBook().getBook();
        String senderNickname;
        if (currentRound.getRoundNumber() == 1) {
            senderNickname = wheelState.getOwnBook().getOwner().getNickname();
        } else if (previousRound == null) {
            senderNickname = null;
        } else {
            WheelState previousWheelState = wheelStateIndex.findByOwnBook(
                    previousRound.getRoundId(),
                    wheelState.getOwnBook().getOwnBookId()
            );
            senderNickname = previousWheelState == null
                    ? null
                    : previousWheelState.getMember().getUser().getNickname();
        }

        return MyStepResponse.of(
                wheelState.getWheelStateId(),
                book.getBookId(),
                wheelState.getWheelState(),
                book.getTitle(),
                book.getCoverImage(),
                senderNickname
        );
    }

    private MyBookStepResponse toUnassignedMyBookStep(OwnBook ownBook) {
        if (ownBook == null) {
            return null;
        }
        Book book = ownBook.getBook();
        return MyBookStepResponse.of(
                book.getBookId(),
                book.getTitle(),
                book.getCoverImage(),
                book.getAuthor(),
                null,
                null,
                null
        );
    }

    private MyBookStepResponse toAssignedMyBookStep(
            OwnBook ownBook,
            WheelState wheelState,
            String location
    ) {
        Book book = ownBook.getBook();
        return MyBookStepResponse.of(
                book.getBookId(),
                book.getTitle(),
                book.getCoverImage(),
                book.getAuthor(),
                wheelState.getMember().getUser().getNickname(),
                wheelState.getWheelState(),
                location
        );
    }

    private int daysBetween(LocalDate startDate, LocalDate endDate) {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate));
    }

    private record RoundSelection(Round targetRound, Round previousRound) {
    }

    private record WheelStateIndex(
            Map<String, Map<String, WheelState>> byRoundAndMember,
            Map<String, Map<String, WheelState>> byRoundAndOwnBook
    ) {
        private static WheelStateIndex empty() {
            return new WheelStateIndex(Map.of(), Map.of());
        }

        private static WheelStateIndex from(List<WheelState> wheelStates) {
            // myStep은 멤버 기준, myBookStep은 등록 도서 기준이므로 두 방향의 인덱스를 만든다.
            Map<String, Map<String, WheelState>> byRoundAndMember = new HashMap<>();
            Map<String, Map<String, WheelState>> byRoundAndOwnBook = new HashMap<>();

            for (WheelState wheelState : wheelStates) {
                byRoundAndMember
                        .computeIfAbsent(wheelState.getRoundId(), ignored -> new HashMap<>())
                        .put(wheelState.getMember().getMemberId(), wheelState);
                byRoundAndOwnBook
                        .computeIfAbsent(wheelState.getRoundId(), ignored -> new HashMap<>())
                        .put(wheelState.getOwnBook().getOwnBookId(), wheelState);
            }

            return new WheelStateIndex(byRoundAndMember, byRoundAndOwnBook);
        }

        private WheelState findByMember(String roundId, String memberId) {
            return byRoundAndMember.getOrDefault(roundId, Map.of()).get(memberId);
        }

        private WheelState findByOwnBook(String roundId, String ownBookId) {
            return byRoundAndOwnBook.getOrDefault(roundId, Map.of()).get(ownBookId);
        }
    }
}
