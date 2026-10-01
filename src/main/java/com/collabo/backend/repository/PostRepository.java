package com.collabo.backend.repository;

import com.collabo.backend.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /** The Gaze: everyone's posts, newest first, minus authors the viewer has a block with. */
    @Query("select p from Post p where p.createdAt < :before and p.authorId not in :hidden "
            + "and (:pendingOnly = false or (p.formed = false and (p.applyBy is null or p.applyBy > :now))) order by p.createdAt desc")
    List<Post> gaze(@Param("before") Instant before, @Param("hidden") Collection<UUID> hidden,
                    @Param("pendingOnly") boolean pendingOnly, @Param("now") Instant now, Pageable page);

    /** Gaze search: posts whose title, body or hashtags contain the (already lower-cased, LIKE-escaped) pattern, newest first. */
    @Query("select p from Post p where p.createdAt < :before and p.authorId not in :hidden "
            + "and (lower(p.title) like :pat escape '!' or lower(p.body) like :pat escape '!' or lower(coalesce(p.hashtags, '')) like :pat escape '!') order by p.createdAt desc")
    List<Post> search(@Param("before") Instant before, @Param("hidden") Collection<UUID> hidden, @Param("pat") String pattern, Pageable page);

    /** Shared Gaze: only posts by the given network of authors. */
    @Query("select p from Post p where p.createdAt < :before and p.authorId in :network "
            + "and (:pendingOnly = false or (p.formed = false and (p.applyBy is null or p.applyBy > :now))) order by p.createdAt desc")
    List<Post> network(@Param("before") Instant before, @Param("network") Collection<UUID> network,
                       @Param("pendingOnly") boolean pendingOnly, @Param("now") Instant now, Pageable page);

    /** One author's posts for their profile, newest first. */
    @Query("select p from Post p where p.authorId = :author and p.createdAt < :before order by p.createdAt desc")
    List<Post> byAuthor(@Param("author") UUID author, @Param("before") Instant before, Pageable page);
}
