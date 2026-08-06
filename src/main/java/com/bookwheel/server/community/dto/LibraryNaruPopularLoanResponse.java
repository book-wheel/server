package com.bookwheel.server.community.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;

public record LibraryNaruPopularLoanResponse(
    PopularLoan response
) {

    public record PopularLoan(
        String errCode,
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<DocItem> docs
    ) {}

    public record DocItem(
        Doc doc
    ) {}

    public record Doc(
        Integer ranking,
        @JsonAlias({"isbn13", "isbn"})
        String isbn,
        @JsonAlias({"loan_count", "loanCnt"})
        Integer loanCount,
        String bookname,
        String authors,
        String publisher,
        @JsonAlias({"publication_year", "publicationYear"})
        String publicationYear,
        @JsonAlias({"bookImageURL", "bookImageUrl", "book_image_url"})
        String bookImageUrl
    ) {}
}
