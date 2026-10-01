package com.collabo.backend.repository;

import com.collabo.backend.entity.ProblemReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProblemReportRepository extends JpaRepository<ProblemReport, UUID> {
    /** Open ones first, newest first within each. */
    List<ProblemReport> findAllByOrderByResolvedAscCreatedAtDesc(Pageable page);
    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant since);
}
