package com.collabo.backend.repository;

import com.collabo.backend.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    // ponytail: no paging, the first 200 is plenty until a post gets a real crowd
    List<PostComment> findTop200ByPostIdOrderByCreatedAtAsc(UUID postId);
    void deleteByPostId(UUID postId);
}
