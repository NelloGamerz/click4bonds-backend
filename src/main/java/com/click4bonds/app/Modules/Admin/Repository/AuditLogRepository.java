package com.click4bonds.app.Modules.Admin.Repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Admin.Model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByPerformedBy_IdAndCreatedAtBetween(
            UUID performedById,
            Instant from,
            Instant to,
            Pageable pageable);
}
