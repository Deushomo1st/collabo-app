package com.collabo.backend.yarn;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface YarnThreadRepository extends JpaRepository<YarnThread, UUID> {
    Optional<YarnThread> findByDmKey(String dmKey);
}
