package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findAllByPost(Post post);

}
