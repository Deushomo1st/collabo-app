package com.collabo.backend.repository;

import com.collabo.backend.entity.Shout;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShoutRepository extends JpaRepository<Shout, UUID> {
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostId(UUID postId);

    /** [postId, count] rows for a page of posts. */
    @Query("select s.postId, count(s) from Shout s where s.postId in :ids group by s.postId")
    List<Object[]> counts(@Param("ids") Collection<UUID> postIds);

    /** Which of these posts the user has shouted. */
    @Query("select s.postId from Shout s where s.userId = :u and s.postId in :ids")
    List<UUID> shoutedAmong(@Param("u") UUID userId, @Param("ids") Collection<UUID> postIds);

    /** Shout-outs by a network of people, newest first, skipping posts by hidden authors. */
    @Query("select s from Shout s, Post p where p.id = s.postId and s.createdAt < :before and s.userId in :network "
            + "and p.authorId not in :hidden and (:pendingOnly = false or p.applyBy is null or p.applyBy > :now) order by s.createdAt desc")
    List<Shout> byNetwork(@Param("before") Instant before, @Param("network") Collection<UUID> network,
                          @Param("hidden") Collection<UUID> hidden, @Param("pendingOnly") boolean pendingOnly,
                          @Param("now") Instant now, Pageable page);

    /** One person's reposts, newest first, skipping posts by hidden authors. */
    @Query("select s from Shout s, Post p where p.id = s.postId and s.userId = :u and s.createdAt < :before "
            + "and p.authorId not in :hidden order by s.createdAt desc")
    List<Shout> byUser(@Param("u") UUID userId, @Param("before") Instant before,
                       @Param("hidden") Collection<UUID> hidden, Pageable page);
}
