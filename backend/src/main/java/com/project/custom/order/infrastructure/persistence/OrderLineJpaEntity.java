package com.project.custom.order.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "order_line")
@IdClass(OrderLineJpaEntity.Key.class)
class OrderLineJpaEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Id
    @Column(name = "line_no")
    private Integer lineNo;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false)
    private int quantity;

    protected OrderLineJpaEntity() {
    }

    OrderLineJpaEntity(UUID orderId, int lineNo, long productId, String name, long unitPriceMinor, int quantity) {
        this.orderId = orderId;
        this.lineNo = lineNo;
        this.productId = productId;
        this.name = name;
        this.unitPriceMinor = unitPriceMinor;
        this.quantity = quantity;
    }

    int getLineNo() {
        return lineNo;
    }

    long getProductId() {
        return productId;
    }

    String getName() {
        return name;
    }

    long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    int getQuantity() {
        return quantity;
    }

    record Key(UUID orderId, Integer lineNo) implements Serializable {
    }
}
