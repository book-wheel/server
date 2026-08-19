package com.bookwheel.server.wheel.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
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
        given(memberRepository.existsByGroup_GroupIdAndUser_Id(groupId, userPK)).willReturn(true);
        given(ownBookRepository.findByOwnBookIdAndGroup_GroupId(foreignOwnBookId, groupId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> wheelService.historyReadingBook(userPK, groupId, foreignOwnBookId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

        then(wheelStateRepository).shouldHaveNoInteractions();
    }
}
