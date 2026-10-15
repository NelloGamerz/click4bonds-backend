package com.click4bonds.app.Modules.Admin.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Model.UserRoleAssertions;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The member of staff who acted.
     *
     * <p>Nullable in the mapping, because an entry can record something the
     * system did rather than something a person did. When a person is named it
     * has to be a member of staff — see {@link #assertStaffRole()}.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private User performedBy;

    /**
     * Refuses an audit entry attributed to a customer account.
     *
     * <p>Audit entries record what the platform's operators did, so the account
     * named has to be one of them. Nothing writes an entry today — the table and
     * this entity exist without a repository yet — but the rule is stated here
     * so that the first writer to be added cannot be the one that gets it wrong,
     * and so that the foreign key the schema already carries has a meaning in
     * code.</p>
     *
     * <p>Only on insert. An entry is a record of something that happened and is
     * not edited afterwards, so there is no update path to police; guarding one
     * would also mean re-validating a performer that was correct when written,
     * which can only fail if the account's role changed since — information the
     * entry is not about.</p>
     */
    @PrePersist
    private void assertStaffRole() {

        if (performedBy != null) {
            UserRoleAssertions.requireStaff(performedBy, "AuditLog");
        }
    }

    private String action;

    private String entityType;

    private UUID entityId;

    @Column(length = 5000)
    private String details;

    @CreationTimestamp
    private Instant createdAt;
}
