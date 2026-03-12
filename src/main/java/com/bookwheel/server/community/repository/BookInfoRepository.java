package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookInfoRepository extends JpaRepository<BookInfo, String> {


}