package com.collabo.backend.repository;

import com.collabo.backend.entity.PostCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, UUID> {
    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);
    void deleteByCommentIdAndUserId(UUID commentId, UUID userId);
    void deleteByCommentId(UUID commentId);
    void deleteByPostId(UUID postId);

    /** [commentId, count] rows for a list of comments. */
    @Query("select l.commentId, count(l) from PostCommentLike l where l.commentId in :ids group by l.commentId")
    List<Object[]> counts(@Param("ids") Collection<UUID> commentIds);

    /** Which of these comments the user has liked. */
    @Query("select l.commentId from PostCommentLike l where l.userId = :u and l.commentId in :ids")
    List<UUID> likedAmong(@Param("u") UUID userId, @Param("ids") Collection<UUID> commentIds);
}
