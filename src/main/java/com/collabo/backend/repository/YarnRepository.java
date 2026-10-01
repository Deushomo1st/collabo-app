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

    /** Words someone wrote, newest first, only in threads I sit in. System notes are not searched. */
    @Query("select y from Yarn y where y.kind = com.collabo.backend.entity.Yarn.Kind.USER and lower(y.body) like :pat escape '!' "
            + "and y.threadId in (select m.threadId from ThreadMember m where m.userId = :me) order by y.createdAt desc")
    List<Yarn> searchMine(@Param("me") UUID me, @Param("pat") String pattern, Pageable page);

    long countByThreadId(UUID threadId);

    List<Yarn> findByThreadIdAndPinnedTrueOrderByCreatedAtAsc(UUID threadId);

    List<Yarn> findByThreadIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID threadId, Instant from, Instant to);

    /** Messages I have not read: newer than my marker and written by someone else. System notes ("bob joined") are not messages. */
    default long countUnread(UUID threadId, Instant after, UUID me) { return countUnreadOfKind(threadId, after, me, Yarn.Kind.USER); }

    @Query("select count(y) from Yarn y where y.threadId = :threadId and y.createdAt > :after "
            + "and y.kind = :kind and y.senderId <> :me")
    long countUnreadOfKind(@Param("threadId") UUID threadId, @Param("after") Instant after, @Param("me") UUID me, @Param("kind") Yarn.Kind kind);
}
