package com.click4bonds.app.Modules.Admin.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.click4bonds.app.Modules.Admin.Model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
