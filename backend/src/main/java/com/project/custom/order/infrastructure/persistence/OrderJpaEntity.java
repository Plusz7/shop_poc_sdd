package com.project.custom.order.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Nationalized;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 14, unique = true)
    private String number;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Nationalized
    @Column(nullable = false, length = 254)
    private String email;

    @Nationalized
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Nationalized
    @Column(nullable = false, length = 120)
    private String street;

    @Column(name = "postal_code", nullable = false, length = 6, columnDefinition = "CHAR(6)")
    private String postalCode;

    @Nationalized
    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 2, columnDefinition = "CHAR(2)")
    private String country;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Nationalized
    @Column(name = "review_reason", length = 200)
    private String reviewReason;

    /** {@code null} until first stored, so that Spring Data persists (not merges) a new order. */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    @OrderBy("lineNo ASC")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    OrderJpaEntity(UUID id, String number, UUID guestId, String email, String fullName, String street,
                   String postalCode, String city, String country, long totalMinor, OffsetDateTime createdAt,
                   List<OrderLineJpaEntity> lines) {
        this.id = id;
        this.number = number;
        this.guestId = guestId;
        this.email = email;
        this.fullName = fullName;
        this.street = street;
        this.postalCode = postalCode;
        this.city = city;
        this.country = country;
        this.totalMinor = totalMinor;
        this.createdAt = createdAt;
        this.lines.addAll(lines);
    }

    UUID getId() {
        return id;
    }

    String getNumber() {
        return number;
    }

    UUID getGuestId() {
        return guestId;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    String getStreet() {
        return street;
    }

    String getPostalCode() {
        return postalCode;
    }

    String getCity() {
        return city;
    }

    String getCountry() {
        return country;
    }

    String getStatus() {
        return status;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    OffsetDateTime getPaidAt() {
        return paidAt;
    }

    String getReviewReason() {
        return reviewReason;
    }

    long getVersion() {
        return version == null ? 0 : version;
    }

    List<OrderLineJpaEntity> getLines() {
        return lines;
    }

    /** Only the lifecycle fields change after creation; the lines and customer details are immutable (FR-017). */
    void updateLifecycle(String status, OffsetDateTime paidAt, String reviewReason) {
        this.status = status;
        this.paidAt = paidAt;
        this.reviewReason = reviewReason;
    }
}
