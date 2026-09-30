package com.collabo.backend.repository;

import com.collabo.backend.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    List<PaymentRecord> findBySpaceIdOrderByCreatedAtDesc(UUID spaceId);
    Optional<PaymentRecord> findByIdAndSpaceId(UUID id, UUID spaceId);
    boolean existsBySpaceIdAndState(UUID spaceId, PaymentRecord.State state);
}
