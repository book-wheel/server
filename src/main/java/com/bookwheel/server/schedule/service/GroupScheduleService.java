package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupScheduleService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OwnBookRepository ownBookRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<GroupScheduleRoundResponse> createSchedule(
            String groupId,
            GroupScheduleCreateRequest request,
            String userId
    ) {
        Group group = findGroupByIdForUpdate(groupId);
        findActiveUserByUserId(userId);
        validateLeaderPermission(groupId, userId);

        // 그룹이 소유한 책의 개수를 기준으로 총 라운드 수를 결정
        long ownBookCount = ownBookRepository.countByGroup_GroupId(groupId);
        if (ownBookCount <= 0) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_OWN_BOOK_REQUIRED);
        }

        Integer readingPeriod = group.getReadingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        LocalDate startDate = request.startDate();
        LocalDate requestedEndDate = request.endDate();
        if (requestedEndDate != null && requestedEndDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        // 그룹 수보다 1 적게 라운드 돌기
        int roundCount = Math.toIntExact(ownBookCount) - 1;

        // 제외할 날짜(단일/범위)들을 병합하여 탐색에 최적화된 달력 객체 생성
        ExcludedCalendar excludedCalendar = normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );
        long requiredUsableDays = (long) roundCount * readingPeriod;

        if (requestedEndDate != null) {
            long usableDaysUntilDeadline = countUsableDaysUntilDeadline(startDate, requestedEndDate, excludedCalendar);
            if (usableDaysUntilDeadline < requiredUsableDays) {
                throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
            }
        }

        // 핵심 로직: 라운드별 시작일과 종료일 계산 (제외된 날짜는 건너뜀)
        List<GroupScheduleRoundResponse> rounds = new ArrayList<>(roundCount);
        LocalDate currentStart = startDate;
        for (int roundNumber = 1; roundNumber <= roundCount; roundNumber++) {
            LocalDate endDate = calculateRoundEndDate(currentStart, readingPeriod, excludedCalendar);
            rounds.add(GroupScheduleRoundResponse.of(roundNumber, currentStart, endDate));
            currentStart = endDate.plusDays(1); // 다음 라운드는 이전 라운드 종료일 다음날부터 시작
        }

        LocalDate calculatedFinalEndDate = rounds.get(rounds.size() - 1).endDate();
        if (requestedEndDate != null && calculatedFinalEndDate.isAfter(requestedEndDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
        }

        // 기존 스케줄 초기화 (이전 논의 내용 반영: Repository 벌크 삭제 사용)
        roundRepository.deleteByGroup_GroupId(groupId);
        group.updateScheduleInfo(startDate, roundCount);

        // 계산된 DTO(rounds)를 Round 엔티티 리스트로 변환
        List<Round> roundEntities = rounds.stream()
                .map(round -> Round.builder()
                        .roundId(UUID.randomUUID().toString())
                        .group(group)
                        .roundNumber(round.roundNumber())
                        .startDate(round.startDate())
                        .endDate(round.endDate())
                        .build())
                .toList();

        // JPA의 saveAll()을 사용하여 한 번에 저장
        roundRepository.saveAll(roundEntities);

        return rounds;
    }

    // 오늘부터 독서를 시작해야하는 그룹을 찾아서 진행중으로 변경
    @Transactional
    public int updateStartedGroupsToInProgress() {
        LocalDate localDate = LocalDate.now();

        return groupRepository.updateGroupStateToInProcess(
                State.IN_PROGRESS,
                State.RECRUITING,
                localDate
        );
    }

    // 끝난 라운드를 종료시키는 로직
    @Transactional
    public int closeExpiredWheelStates() {
        LocalDate localDate = LocalDate.now();

        // 1.'오늘'을 기준으로 종료일이 지난 roundId 리스트 조회
        List<String> expiredRoundIds = roundRepository.findRoundIdsByEndDateBefore(localDate);

        // 만약 끝난 라운드가 하나도 없다면(비어있다면) 메서드 종료
        if (expiredRoundIds.isEmpty()) return 0;

        // 2. expiredRoundIds에 속하면서, 아직 완료되지 않은 책바퀴 가져옴.
        List<WheelState> wheelsToClose = wheelStateRepository.findByRoundIdInAndIsCompletedFalse(expiredRoundIds);

        int cnt = 0;
        // 3. 가져온 책바퀴들을 for문으로 돌면서 상태를 '완료'로 바꿔줍니다.
        for (WheelState wheel : wheelsToClose) {
            wheel.closeWheel();
            cnt++;
        }

        return cnt;
    }

    @Transactional
    public int startRoundWheelState() {
        LocalDate localDate = LocalDate.now();

        // 1. 오늘 시작하는 라운드 조회
        List<Round> startingRounds = roundRepository.findByStartDate(localDate);

        // 없을 경우, 0 리턴
        if (startingRounds.isEmpty()) return 0;

        int cnt = 0;
        // 책바퀴를 담을 바구니
        List<WheelState> newWheels = new ArrayList<>();

        // 2. 라운드 하나씩 계산
        for (Round round : startingRounds) {
            String groupId = round.getGroup().getGroupId();

            //groupId에 속한 멤버들을 읽는 순서대로 정렬하기
            List<Member> members = memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(groupId, MemberStatus.ACTIVE);

            // 읽는 순서가 지정되어있지 않다면, 임의로 정렬하기
            if (!members.isEmpty() && members.get(0).getReadOrder() == null) {
                members.sort(Comparator.comparing(Member::getMemberId));
            }

            // 이 그룹아이디에 등록된 책 목록 가져오기
            List<OwnBook> books = ownBookRepository.findByGroup_GroupId(groupId);

            // 3. 멤버들과 책을 연결해서 WheelState를 만들어 저장

            // 누가 어떤 책의 주인인지 찾기 쉽게 Map 으로 연결
            Map<String, OwnBook> bookMap = books.stream()
                    .collect(Collectors.toMap(b -> b.getOwner().getId(), b -> b));

            // 멤버 순서대로 책을 뽑아서 정렬된 책 리스트 생성
            List<OwnBook> sortedBooks = members.stream()
                    .map(m->bookMap.get(m.getUser().getId()))
                    .toList();

            int size = members.size();
            int currentRound = round.getRoundNumber(); //현재 라운드

            for (int i = 0; i < size; i++) {
                Member member = members.get(i);

                int bookIndex = (i + currentRound) % size;
                OwnBook assignedBook = sortedBooks.get(bookIndex);

                WheelState newWheel = WheelState.builder()
                        .wheelStateId(UUID.randomUUID().toString())
                        .roundId(round.getRoundId())
                        .member(member)
                        .ownBook(assignedBook)
                        .build();

                newWheels.add(newWheel);
                cnt++;
            }
        }
        // 한 번에 DB에 저장
        wheelStateRepository.saveAll(newWheels);
        return cnt;
    }

    // 모든 라운드가 끝난 그룹을 COMPLETE 상태로 변경
    @Transactional
    public int closeFinishedGroups() {
        LocalDate today = LocalDate.now();

        return groupRepository.updateFinishedGroupsToComplete(
                State.COMPLETE,
                State.IN_PROGRESS,
                today
        );
    }

    // 제외된 날짜를 건너뛰며 실제 독서 기간(readingPeriod)을 채우는 종료일 계산
    private LocalDate calculateRoundEndDate(
            LocalDate currentStart,
            int readingPeriod,
            ExcludedCalendar excludedCalendar
    ) {
        LocalDate cursor = currentStart;
        int validDayCount = 0;

        while (validDayCount < readingPeriod) {
            if (!excludedCalendar.isExcluded(cursor)) {
                validDayCount++; // 제외된 날짜가 아닐 때만 유효 기간 1일 증가
            }
            if (validDayCount == readingPeriod) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }

        return cursor;
    }

    private ExcludedCalendar normalizeExcludedCalendar(
            List<LocalDate> excludedDates,
            List<ExcludedDateRange> excludedDateRanges
    ) {
        List<DateRange> ranges = new ArrayList<>();
        if (excludedDates != null) {
            for (LocalDate excludedDate : excludedDates) {
                if (excludedDate == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                ranges.add(new DateRange(excludedDate, excludedDate));
            }
        }

        if (excludedDateRanges != null) {
            for (ExcludedDateRange range : excludedDateRanges) {
                validateExcludedDateRange(range);
                ranges.add(new DateRange(range.startDate(), range.endDate()));
            }
        }

        return new ExcludedCalendar(mergeRanges(ranges));
    }

    private void validateExcludedDateRange(ExcludedDateRange range) {
        if (range == null || range.startDate() == null || range.endDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (range.endDate().isBefore(range.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 겹치거나 이어지는 제외 기간들을 하나의 구간으로 병합하여 연산 효율성 극대화
    private List<DateRange> mergeRanges(List<DateRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }

        List<DateRange> sorted = ranges.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DateRange::startDate).thenComparing(DateRange::endDate))
                .toList();

        // 시작일 기준으로 정렬
        List<DateRange> merged = new ArrayList<>();
        DateRange current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            DateRange next = sorted.get(i);
            boolean isOverlappedOrAdjacent = !next.startDate().isAfter(current.endDate().plusDays(1));

            if (isOverlappedOrAdjacent) {
                // 두 구간이 겹치거나 맞닿아 있다면 하나로 병합
                LocalDate mergedEnd = current.endDate().isAfter(next.endDate()) ? current.endDate() : next.endDate();
                current = new DateRange(current.startDate(), mergedEnd);
                continue;
            }

            merged.add(current);
            current = next;
        }

        merged.add(current);
        return merged;
    }

    private long countUsableDaysUntilDeadline(
            LocalDate startDate,
            LocalDate requestedEndDate,
            ExcludedCalendar excludedCalendar
    ) {
        return excludedCalendar.countUsableDaysInInterval(startDate, requestedEndDate);
    }

    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }

    private void validateLeaderPermission(String groupId, String userId) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_UserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_LEADER_ONLY));

        boolean isLeader = member.getMemberRole() == MemberRole.LEADER;
        boolean isActive = member.getMemberStatus() == MemberStatus.ACTIVE;
        if (!isLeader || !isActive) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_ONLY);
        }
    }

    private static final class ExcludedCalendar {
        private final List<DateRange> mergedExcludedRanges;

        private ExcludedCalendar(List<DateRange> mergedExcludedRanges) {
            this.mergedExcludedRanges = List.copyOf(mergedExcludedRanges);
        }

        // 성능 최적화: 제외 기간 목록이 시작일 기준으로 정렬되어 있으므로,
        // 이분 탐색(Binary Search, O(logN))을 사용하여 특정 날짜가 제외 기간에 포함되는지 매우 빠르게 확인
        private boolean isExcluded(LocalDate date) {
            int left = 0;
            int right = mergedExcludedRanges.size() - 1;

            while (left <= right) {
                int mid = (left + right) >>> 1;
                DateRange range = mergedExcludedRanges.get(mid);

                if (date.isBefore(range.startDate())) {
                    right = mid - 1;
                    continue;
                }

                if (date.isAfter(range.endDate())) {
                    left = mid + 1;
                    continue;
                }

                if (range.contains(date)) {
                    return true;
                }
            }

            return false;
        }

        private long countUsableDaysInInterval(LocalDate startDate, LocalDate endDate) {
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
            return totalDays - countExcludedDaysInInterval(startDate, endDate);
        }

        private long countExcludedDaysInInterval(LocalDate startDate, LocalDate endDate) {
            long excludedDays = 0L;

            for (DateRange range : mergedExcludedRanges) {
                if (range.endDate().isBefore(startDate)) {
                    continue;
                }
                if (range.startDate().isAfter(endDate)) {
                    break;
                }

                LocalDate overlapStart = range.startDate().isAfter(startDate) ? range.startDate() : startDate;
                LocalDate overlapEnd = range.endDate().isBefore(endDate) ? range.endDate() : endDate;
                excludedDays += ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1L;
            }

            return excludedDays;
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        private boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }
}
