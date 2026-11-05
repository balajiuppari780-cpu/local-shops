package com.localshops.dto;

import com.localshops.model.Order;
import com.localshops.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private Long shopId;
    private String shopName;
    private BigDecimal total;
    private OrderStatus status;
    private String idempotencyKey;
    private List<OrderItemResponse> items;
    private Instant createdAt;

    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(OrderItemResponse::fromEntity).toList()
                : Collections.emptyList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .shopId(order.getShop() != null ? order.getShop().getId() : null)
                .shopName(order.getShop() != null ? order.getShop().getName() : null)
                .total(order.getTotal())
                .status(order.getStatus())
                .idempotencyKey(order.getIdempotencyKey())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}

