package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WheelReassignmentService {
    private final RoundRepository roundRepository;
    private final OwnBookRepository ownBookRepository;
    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    public WheelReassignmentService(
            RoundRepository roundRepository,
            OwnBookRepository ownBookRepository,
            WheelStateRepository wheelStateRepository,
            Clock clock
    ) {
        this.roundRepository = roundRepository;
        this.ownBookRepository = ownBookRepository;
        this.wheelStateRepository = wheelStateRepository;
        this.clock = clock;
    }

    @Transactional
    public void reassignFutureRounds(String groupId, Member targetMember, List<Member> remainingMembers) {
        LocalDate today = LocalDate.now(clock);
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        // 오늘 이전에 시작한 라운드는 독서 기록으로 보고 절대 다시 배정하지 않는다.
        List<Round> futureRounds = futureRounds(rounds, today);
        if (futureRounds.isEmpty()) {
            return;
        }

        List<Member> sortedMembers = sortMembers(remainingMembers);
        List<String> protectedRoundIds = protectedRoundIds(rounds, today);
        List<WheelState> protectedStates = protectedRoundIds.isEmpty()
                ? List.of()
                : wheelStateRepository.findByRoundIdIn(protectedRoundIds);
        List<OwnBook> sortedBooks = sortedBooks(groupId, sortedMembers, targetMember);
        List<WheelState> replacements = buildReplacements(futureRounds, sortedMembers, sortedBooks, protectedStates);

        // 전체 미래 라운드의 해를 먼저 찾은 뒤에만 기존 미래 배정을 교체한다.
        wheelStateRepository.deleteByRoundIdIn(roundIds(futureRounds));
        wheelStateRepository.flush();
        wheelStateRepository.saveAll(replacements);
    }

    private List<WheelState> buildReplacements(
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            List<WheelState> protectedStates
    ) {
        Map<String, Set<String>> assignedBookIdsByMember = assignedBookIdsByMember(members, protectedStates);
        List<WheelState> replacements = new ArrayList<>(futureRounds.size() * members.size());

        // 한 라운드씩 탐욕적으로 고르면 뒤 라운드가 막힐 수 있어, 모든 미래 라운드를 백트래킹으로 함께 검증한다.
        if (members.isEmpty()
                || !matchFutureRounds(0, futureRounds, members, books, assignedBookIdsByMember, replacements)) {
            throw new BusinessException(ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE);
        }

        return replacements;
    }

    private boolean matchFutureRounds(
            int roundIndex,
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            Map<String, Set<String>> assignedBookIdsByMember,
            List<WheelState> replacements
    ) {
        if (roundIndex == futureRounds.size()) {
            return true;
        }

        return matchRoundMember(
                roundIndex,
                0,
                futureRounds,
                members,
                books,
                assignedBookIdsByMember,
                new HashSet<>(),
                replacements
        );
    }

    private boolean matchRoundMember(
            int roundIndex,
            int memberIndex,
            List<Round> futureRounds,
            List<Member> members,
            List<OwnBook> books,
            Map<String, Set<String>> assignedBookIdsByMember,
            Set<String> usedBookIds,
            List<WheelState> replacements
    ) {
        if (memberIndex == members.size()) {
            return matchFutureRounds(roundIndex + 1, futureRounds, members, books, assignedBookIdsByMember, replacements);
        }

        Round round = futureRounds.get(roundIndex);
        Member member = members.get(memberIndex);
        for (OwnBook book : books) {
            String ownBookId = book.getOwnBookId();
            if (usedBookIds.contains(ownBookId) || !canAssign(member, book, assignedBookIdsByMember)) {
                continue;
            }

            usedBookIds.add(ownBookId);
            assignedBookIdsByMember.get(member.getMemberId()).add(ownBookId);
            replacements.add(WheelState.builder()
                    .wheelStateId(UUID.randomUUID().toString())
                    .roundId(round.getRoundId())
                    .member(member)
                    .ownBook(book)
                    .build());
            if (matchRoundMember(
                    roundIndex,
                    memberIndex + 1,
                    futureRounds,
                    members,
                    books,
                    assignedBookIdsByMember,
                    usedBookIds,
                    replacements
            )) {
                return true;
            }
            replacements.remove(replacements.size() - 1);
            assignedBookIdsByMember.get(member.getMemberId()).remove(ownBookId);
            usedBookIds.remove(ownBookId);
        }

        return false;
    }

    private boolean canAssign(Member member, OwnBook book, Map<String, Set<String>> assignedBookIdsByMember) {
        return !member.getUser().getId().equals(book.getOwner().getId())
                && !assignedBookIdsByMember.get(member.getMemberId()).contains(book.getOwnBookId());
    }

    private Map<String, Set<String>> assignedBookIdsByMember(List<Member> members, List<WheelState> protectedStates) {
        Set<String> memberIds = members.stream()
                .map(Member::getMemberId)
                .collect(Collectors.toSet());
        Map<String, Set<String>> assignedBookIdsByMember = new HashMap<>();
        members.forEach(member -> assignedBookIdsByMember.put(member.getMemberId(), new HashSet<>()));

        protectedStates.stream()
                .filter(state -> memberIds.contains(state.getMember().getMemberId()))
                .forEach(state -> assignedBookIdsByMember.get(state.getMember().getMemberId())
                        .add(state.getOwnBook().getOwnBookId()));

        return assignedBookIdsByMember;
    }

    private List<OwnBook> sortedBooks(String groupId, List<Member> members, Member targetMember) {
        Map<String, Integer> ownerOrder = new HashMap<>();
        for (int index = 0; index < members.size(); index++) {
            ownerOrder.put(members.get(index).getUser().getId(), index);
        }
        String removedUserPK = targetMember.getUser().getId();

        return ownBookRepository.findByGroup_GroupIdIn(List.of(groupId)).stream()
                // 남아 있는 ACTIVE 멤버의 책만 후보로 두어 탈퇴자 책을 이후 배정에서 제외한다.
                .filter(book -> ownerOrder.containsKey(book.getOwner().getId()))
                .filter(book -> !removedUserPK.equals(book.getOwner().getId()))
                .sorted(Comparator
                        .comparing((OwnBook book) -> ownerOrder.get(book.getOwner().getId()))
                        .thenComparing(OwnBook::getOwnBookId))
                .toList();
    }

    private List<Member> sortMembers(List<Member> members) {
        return members.stream()
                .sorted(Comparator
                        .comparing(Member::getReadOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Member::getMemberId))
                .toList();
    }

    private List<Round> futureRounds(List<Round> rounds, LocalDate today) {
        return rounds.stream()
                .filter(round -> round.getStartDate() != null && round.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Round::getStartDate).thenComparing(Round::getRoundNumber))
                .toList();
    }

    private List<String> protectedRoundIds(List<Round> rounds, LocalDate today) {
        return rounds.stream()
                // 현재 진행 중인 라운드도 보호 대상이라, 이미 읽은 책으로 간주한다.
                .filter(round -> round.getStartDate() != null && !round.getStartDate().isAfter(today))
                .map(Round::getRoundId)
                .toList();
    }

    private Collection<String> roundIds(List<Round> rounds) {
        return rounds.stream()
                .map(Round::getRoundId)
                .toList();
    }
}
