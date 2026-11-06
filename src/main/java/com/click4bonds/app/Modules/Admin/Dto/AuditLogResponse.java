package com.click4bonds.app.Modules.Admin.Dto;

import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.Admin.Model.AuditLog;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.User;

/**
 * One audit entry, as the log viewer reads it.
 *
 * <p>The actor is flattened to a name and address rather than returned as an
 * account object: the viewer already knows whose log it asked for, and an entry
 * may have no actor at all (one written by the system).</p>
 */
public record AuditLogResponse(
        UUID id,
        UUID performedById,
        String performedByName,
        String performedByEmail,
        UserRole performedByRole,
        String action,
        String entityType,
        UUID entityId,
        String details,
        String ipAddress,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {

        User actor = log.getPerformedBy();

        return new AuditLogResponse(
                log.getId(),
                actor == null ? null : actor.getId(),
                actor == null ? null : fullName(actor),
                actor == null ? null : actor.getEmail(),
                log.getPerformedByRole(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt());
    }

    private static String fullName(User user) {

        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();

        return (firstName + " " + lastName).trim();
    }
}
