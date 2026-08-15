package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookDetailLookupService {

    private final AladinService aladinService;
    private final PopularLoanBookRepository popularLoanBookRepository;

    public BookDetailResponse getBookDetailByIsbn(String isbn, boolean isInterested) {
        try {
            return aladinService.getBookDetailByIsbn(isbn, isInterested);
        } catch (BusinessException e) {
            if (!canUsePopularLoanFallback(e)) {
                throw e;
            }
            return findPopularLoanFallback(isbn, isInterested, e);
        }
    }

    private BookDetailResponse findPopularLoanFallback(
        String isbn,
        boolean isInterested,
        BusinessException originalException
    ) {
        PopularLoanBook book = popularLoanBookRepository
            .findFirstByIsbnOrderByEndDateDescStartDateDescCollectedAtDesc(isbn)
            .orElseThrow(() -> originalException);

        log.warn(
            "알라딘 도서 상세 조회 실패. 인기 대출 메타데이터로 대체합니다. - ISBN: {}, 오류: {}",
            isbn,
            originalException.getErrorCode().getCode()
        );
        return BookDetailResponse.from(book, isInterested);
    }

    private boolean canUsePopularLoanFallback(BusinessException e) {
        return e.getErrorCode() == ErrorCode.ALADIN_API_ERROR
            || e.getErrorCode() == ErrorCode.BOOK_NOT_FOUND;
    }
}
