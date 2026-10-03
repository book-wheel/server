package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.entity.PostCommentReport;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentReportRepository extends JpaRepository<PostCommentReport, Long> {
    boolean existsByCommentAndReporter(PostComment comment, User reporter);
}
