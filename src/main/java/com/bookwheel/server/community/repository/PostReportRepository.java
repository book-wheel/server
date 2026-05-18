package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostReport;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    //중복 신고 여부
    boolean existsByPostAndReporter(Post post, User reporter);
}
