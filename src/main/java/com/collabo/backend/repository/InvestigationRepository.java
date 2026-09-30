package com.collabo.backend.repository;

import com.collabo.backend.entity.Investigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {
    List<Investigation> findTop200ByStatusInOrderByCreatedAtAsc(Collection<Investigation.Status> statuses);
    List<Investigation> findTop200ByStatusOrderByCreatedAtDesc(Investigation.Status status);
    Optional<Investigation> findByAppealId(UUID appealId);
    List<Investigation> findByModeratorIdAndStatusInOrderByAssignedAtAsc(UUID moderatorId, Collection<Investigation.Status> statuses);
    boolean existsByThreadIdAndReporterIdAndKindAndStatusNot(UUID threadId, UUID reporterId, Investigation.Kind kind, Investigation.Status status);
}
