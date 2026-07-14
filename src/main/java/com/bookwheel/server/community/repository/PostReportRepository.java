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

    @Modifying
    @Query("delete from PostReport report where report.post.postId in :postIds")
    // 게시물 삭제 전에 신고 기록이 참조하는 게시물을 먼저 제거한다.
    void deleteAllByPostIds(@Param("postIds") java.util.Collection<Long> postIds);
}
