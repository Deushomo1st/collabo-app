package com.collabo.backend.repository;

import com.collabo.backend.entity.Plea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PleaRepository extends JpaRepository<Plea, UUID> {
    List<Plea> findByProcessIdIn(Collection<UUID> processIds);
    List<Plea> findBySpaceIdAndEndsAtAfter(UUID spaceId, Instant now);
    boolean existsBySpaceIdAndPleaderIdAndCreatedAtAfter(UUID spaceId, UUID pleaderId, Instant since);
}
