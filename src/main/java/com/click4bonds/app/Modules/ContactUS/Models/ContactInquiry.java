package com.click4bonds.app.Modules.ContactUS.Models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.click4bonds.app.Modules.ContactUS.enums.ContactInquiryStatus;
import com.click4bonds.app.Modules.ContactUS.enums.InvestmentProduct;
import com.click4bonds.app.Modules.ContactUS.enums.InvestmentTimeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contact_inquiries",

        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contact_inquiries_email", columnNames = "email")
        },

        indexes = {
                @Index(name = "idx_contact_inquiries_created_at", columnList = "created_at"),
                @Index(name = "idx_contact_inquiries_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /*
     * User information
     */

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    /*
     * Investment requirements
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_timeline", nullable = false, length = 30)
    private InvestmentTimeline investmentTimeline;

    @Enumerated(EnumType.STRING)
    @Column(name = "what_are_you_looking_for", nullable = false, length = 50)
    private InvestmentProduct whatAreYouLookingFor;

    /**
     * Used only when whatAreYouLookingFor == OTHER.
     */
    @Column(name = "other_what_are_you_looking_for", length = 500)
    private String otherWhatAreYouLookingFor;

    /*
     * Contact inquiry workflow
     */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ContactInquiryStatus status = ContactInquiryStatus.NEW;

    /*
     * Request metadata
     */

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "referer", length = 2000)
    private String referer;

    @Column(name = "forwarded_for", length = 1000)
    private String forwardedFor;

    /*
     * Geographic information
     */

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /*
     * Audit timestamps
     */

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * Entity lifecycle
     */

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
