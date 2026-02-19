package com.bookwheel.server.book.repository;

import com.bookwheel.server.book.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {
    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);
}
