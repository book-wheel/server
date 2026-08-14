package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentReadingBookService {

    private final WheelStateRepository wheelStateRepository;
    private final Clock clock;

    public CurrentReadingBooksResponse getCurrentReadingBooks(String userPK) {
        LocalDate today = LocalDate.now(clock);
        return new CurrentReadingBooksResponse(
            wheelStateRepository.findCurrentReadingBooks(userPK, today)
        );
    }
}
