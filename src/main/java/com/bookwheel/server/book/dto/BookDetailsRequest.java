package com.bookwheel.server.book.dto;

import java.time.LocalDate;

public interface BookDetailsRequest {
    String isbn();
    String title();
    String author();
    String publisher();
    LocalDate pubDate();
    String coverImage();
    Integer totalPage();
}