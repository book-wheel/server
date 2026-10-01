package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostReport;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    //중복 신고 여부
    boolean existsByPostAndReporter(Post post, User reporter);

    @Modifying(flushAutomatically = true)
    @Query("delete from PostReport report where report.post.postId = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}
