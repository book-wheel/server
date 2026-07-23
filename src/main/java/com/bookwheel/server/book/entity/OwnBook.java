package com.bookwheel.server.book.entity;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@Table(name = "own_book")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnBook {
    @Id
    @Column(name = "ownbook_id", length = 50)
    private String ownBookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", referencedColumnName = "book_id", nullable = false)
    private Book book;

    @Column(name = "book_condition", length = 100)
    private String bookCondition;

    @Column(name = "note_to_reader", columnDefinition = "TEXT")
    private String noteToReader;

    public void update(
        Book book,
        String bookCondition,
        String noteToReader
) {
    this.book = book;
    this.bookCondition = bookCondition;
    this.noteToReader = noteToReader;
}
}
