package com.bookwheel.server.community.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_info_id")
    private Long bookInfoId;


    @Column(name = "isbn", length = 20, nullable = false, unique = true)
    private String isbn;
}
