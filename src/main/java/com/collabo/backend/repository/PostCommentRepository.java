package com.collabo.backend.repository;

import com.collabo.backend.entity.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    // ponytail: no paging, the first 200 is plenty until a post gets a real crowd
    List<PostComment> findTop200ByPostIdOrderByCreatedAtAsc(UUID postId);
    void deleteByPostId(UUID postId);

    List<PostComment> findByParentId(UUID parentId);
    long countByParentId(UUID parentId);

    /** [postId, count] rows for a page of posts. */
    @Query("select c.postId, count(c) from PostComment c where c.postId in :ids group by c.postId")
    List<Object[]> counts(@Param("ids") Collection<UUID> postIds);

    /** The newest top-level comments across these posts, for the feed's little preview (replies stay in the thread). */
    @Query("select c from PostComment c where c.postId in :ids and c.parentId is null order by c.createdAt desc")
    List<PostComment> recent(@Param("ids") Collection<UUID> postIds, Pageable page);
}
