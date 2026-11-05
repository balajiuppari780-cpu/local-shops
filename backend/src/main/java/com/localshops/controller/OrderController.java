package com.localshops.controller;

import com.localshops.dto.CreateOrderRequest;
import com.localshops.dto.OrderResponse;
import com.localshops.dto.PageResponse;
import com.localshops.dto.UpdateOrderStatusRequest;
import com.localshops.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement, idempotent checkout, status tracking, and fulfillment")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Place order", description = "Customer places an order with idempotency protection and atomic stock reduction")
    public OrderResponse placeOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Optional client idempotency key to prevent double charging on retry")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal
    ) {
        return orderService.placeOrderWithRetry(request, principal.getName(), idempotencyKey);
    }

    @GetMapping("/orders/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "My order history", description = "Customer views their own past orders with pagination")
    public PageResponse<OrderResponse> getMyOrders(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal
    ) {
        return orderService.getCustomerOrders(principal.getName(), pageable);
    }

    @GetMapping("/shops/{shopId}/orders")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Shop orders", description = "Shop owner views incoming customer orders for their shop")
    public PageResponse<OrderResponse> getShopOrders(
            @PathVariable Long shopId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal
    ) {
        return orderService.getShopOrders(shopId, principal.getName(), pageable);
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SHOP_OWNER')")
    @Operation(summary = "Update order status", description = "Transitions order status following state machine rules")
    public OrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Principal principal
    ) {
        return orderService.updateOrderStatus(id, request.getStatus(), principal.getName());
    }
}

