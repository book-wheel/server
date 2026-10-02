package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostCommentResponseTest {
    private PostCommentResponse response(User author) {
        PostComment comment = PostComment.builder().postCommentId(1L)
            .post(Post.builder().postId(2L).build()).user(author).content("comment").build();
        return PostCommentResponse.of(comment, null, false);
    }

    @Test void activeAuthorHasUserPK() {
        User author = User.builder().nickname("author").build();
        assertThat(response(author).userPK()).isEqualTo(author.getId());
    }

    @Test void inactiveAuthorHasNoUserPK() {
        User author = User.builder().nickname("withdrawn").isActive(false).build();
        assertThat(response(author).userPK()).isNull();
        assertThat(response(author).author()).isEqualTo("탈퇴한 사용자");
    }

    @Test void purgedAuthorHasNoUserPK() {
        assertThat(response(null).userPK()).isNull();
    }
}
