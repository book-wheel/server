package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Book exchange recommendation item")
public record BookExchangeRecommendationBook(
    @Schema(description = "ISBN", example = "9788954681179")
    String isbn,

    @Schema(description = "Book title", example = "밝은 밤")
    String title,

    @Schema(description = "Book author", example = "최은영")
    String author,

    @Schema(description = "Book cover image URL", nullable = true)
    String coverImageUrl,

    @Schema(description = "Data4Library popularity rank", example = "1")
    int data4LibraryRank,

    @Schema(description = "Data4Library loan count for the source period", example = "104490")
    int data4LibraryLoanCount,

    @Schema(description = "BookWheel interest count", example = "12")
    long likeCount,

    @Schema(description = "Whether the current user is interested in this book", example = "true")
    boolean isInterested,

    @Schema(description = "Representative public review. Null when no public review exists.", nullable = true)
    BookExchangeRecommendationReview review
) {
}
