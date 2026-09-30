package com.collabo.backend.repository;

import com.collabo.backend.entity.Yarn;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface YarnRepository extends JpaRepository<Yarn, UUID> {

    List<Yarn> findByThreadIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID threadId, Instant before, Pageable page);

    long countByThreadId(UUID threadId);

    List<Yarn> findByThreadIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID threadId, Instant from, Instant to);

    /** Messages I have not read: newer than my marker and written by someone else. System notes ("bob joined") are not messages. */
    default long countUnread(UUID threadId, Instant after, UUID me) { return countUnreadOfKind(threadId, after, me, Yarn.Kind.USER); }

    @Query("select count(y) from Yarn y where y.threadId = :threadId and y.createdAt > :after "
            + "and y.kind = :kind and y.senderId <> :me")
    long countUnreadOfKind(@Param("threadId") UUID threadId, @Param("after") Instant after, @Param("me") UUID me, @Param("kind") Yarn.Kind kind);
}
