package com.example.cinema.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "snack_order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnackOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snackOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snack_order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SnackOrder snackOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "snack_id", nullable = false)
    private Snack snack;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double priceAtPurchase;

    public Double getSubtotal() {
        return (priceAtPurchase == null ? 0.0 : priceAtPurchase) * (quantity == null ? 0 : quantity);
    }
}
