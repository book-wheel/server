package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupScheduleService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OwnBookRepository ownBookRepository;
    private final JdbcTemplate jdbcTemplate;

    // 동적으로 테이블을 생성할 때 발생할 수 있는 동시성 이슈를 제어하기 위한 락 객체와 플래그
    private final Object roundTableInitLock = new Object();
    private volatile boolean roundTableInitialized;

    @Transactional
    public List<GroupScheduleRoundResponse> createSchedule(
            String groupId,
            GroupScheduleCreateRequest request,
            String userId
    ) {
        Group group = findGroupById(groupId);
        findActiveUserByUserId(userId);
        validateLeaderPermission(groupId, userId);

        ensureRoundTableExists();

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

        int roundCount = Math.toIntExact(ownBookCount);

        // 제외할 날짜(단일/범위)들을 병합하여 탐색에 최적화된 달력 객체 생성
        ExcludedCalendar excludedCalendar = normalizeExcludedCalendar(request.excludedDates(), request.excludedDateRanges());
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

        // 기존 스케줄 초기화
        jdbcTemplate.update("DELETE FROM `round` WHERE group_id = ?", groupId);
        group.updateScheduleInfo(startDate, roundCount);

        // 성능 최적화: 다량의 라운드 데이터를 단건 INSERT가 아닌 Batch Insert로 한 번에 처리
        jdbcTemplate.batchUpdate(
                "INSERT INTO `round` (round_id, group_id, round_number, start_date, end_date) VALUES (?, ?, ?, ?, ?)",
                rounds,
                500,
                (ps, round) -> {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, groupId);
                    ps.setInt(3, round.roundNumber());
                    ps.setObject(4, round.startDate());
                    ps.setObject(5, round.endDate());
                }
        );

        return rounds;
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

    // 동시성 제어: Double-Checked Locking(DCL) 패턴을 사용하여 멀티스레드 환경에서 테이블이 단 한 번만 생성되도록 보장
    private void ensureRoundTableExists() {
        if (roundTableInitialized) {
            return;
        }

        synchronized (roundTableInitLock) {
            if (roundTableInitialized) {
                return;
            }

            try {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS `round` (
                            `round_id` VARCHAR(50) PRIMARY KEY,
                            `group_id` VARCHAR(50) NOT NULL,
                            `round_number` INT NOT NULL,
                            `start_date` DATE,
                            `end_date` DATE,
                            CONSTRAINT `fk_round_group`
                                FOREIGN KEY (`group_id`) REFERENCES `reading_group`(`group_id`) ON DELETE CASCADE
                        )
                        """);
                roundTableInitialized = true;
            } catch (DataAccessException e) {
                throw new BusinessException(ErrorCode.GROUP_ROUND_TABLE_NOT_FOUND);
            }
        }
    }

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
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
