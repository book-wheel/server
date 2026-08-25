package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WheelServiceTest {

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private S3Service s3Service;

    private WheelService wheelService;

    @BeforeEach
    void setUp() {
        wheelService = new WheelService(
                wheelStateRepository,
                memberRepository,
                roundRepository,
                ownBookRepository,
                s3Service,
                event -> { },
                Clock.systemUTC()
        );
    }

    @Test
    @DisplayName("다른 모임에 속한 도서의 완독 히스토리는 조회할 수 없다")
    void historyReadingBook_RejectsOwnBookOutsideGroup() {
        String userPK = "user-pk";
        String groupId = "group-1";
        String foreignOwnBookId = "own-book-from-another-group";
        given(memberRepository.existsByGroup_GroupIdAndUser_IdAndMemberStatus(
                groupId,
                userPK,
                MemberStatus.ACTIVE
        )).willReturn(true);
        given(ownBookRepository.findByOwnBookIdAndGroup_GroupId(foreignOwnBookId, groupId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> wheelService.historyReadingBook(userPK, groupId, foreignOwnBookId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

        then(wheelStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PENDING·REJECTED 등 ACTIVE가 아닌 멤버는 책별 완독 히스토리를 조회할 수 없다")
    void historyReadingBook_RejectsInactiveMember() {
        String userPK = "inactive-user-pk";
        String groupId = "group-1";

        given(memberRepository.existsByGroup_GroupIdAndUser_IdAndMemberStatus(
                groupId,
                userPK,
                MemberStatus.ACTIVE
        )).willReturn(false);

        assertThatThrownBy(() -> wheelService.historyReadingBook(userPK, groupId, "own-book-id"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        then(roundRepository).shouldHaveNoInteractions();
        then(ownBookRepository).shouldHaveNoInteractions();
        then(wheelStateRepository).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("요청자와 조회 대상 중 한 명이라도 ACTIVE가 아니면 멤버별 완독 히스토리를 조회할 수 없다")
    void historyReading_RejectsWhenEitherMemberIsInactive() {
        String userPK = "requester-user-pk";
        String targetUserPK = "target-user-pk";
        String groupId = "group-1";

        given(memberRepository.countByGroup_GroupIdAndUser_IdInAndMemberStatus(
                groupId,
                List.of(userPK, targetUserPK),
                MemberStatus.ACTIVE
        )).willReturn(1L);

        assertThatThrownBy(() -> wheelService.historyReading(userPK, targetUserPK, groupId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        then(roundRepository).shouldHaveNoInteractions();
        then(wheelStateRepository).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
    }
}
