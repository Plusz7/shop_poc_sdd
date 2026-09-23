package com.project.custom.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.io.Serializable;

@Entity
@Table(name = "product_image")
@IdClass(ProductImageJpaEntity.Key.class)
class ProductImageJpaEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "display_order")
    private int displayOrder;

    @Nationalized
    @Column(nullable = false, length = 300)
    private String url;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String alt;

    protected ProductImageJpaEntity() {
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    String getUrl() {
        return url;
    }

    String getAlt() {
        return alt;
    }

    record Key(Long productId, int displayOrder) implements Serializable {
    }
}
