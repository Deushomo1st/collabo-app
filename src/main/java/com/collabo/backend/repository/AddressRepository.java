package com.collabo.backend.repository;

import com.collabo.backend.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByRecordIdInOrderByCreatedAtAsc(Collection<UUID> recordIds);
}
