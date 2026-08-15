package com.click4bonds.app.Modules.Admin.Model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.click4bonds.app.Modules.User.Model.User;

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

    @ManyToOne(fetch = FetchType.LAZY)
    private User performedBy;

    private String action;

    private String entityType;

    private UUID entityId;

    @Column(length = 5000)
    private String details;

    @CreationTimestamp
    private Instant createdAt;
}
