package com.localshops.service;

import com.localshops.dto.CreateOrderRequest;
import com.localshops.dto.OrderItemRequest;
import com.localshops.dto.OrderResponse;
import com.localshops.dto.PageResponse;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.ForbiddenException;
import com.localshops.exception.ResourceNotFoundException;
import com.localshops.model.Order;
import com.localshops.model.OrderItem;
import com.localshops.model.OrderStatus;
import com.localshops.model.Product;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.User;
import com.localshops.repository.OrderRepository;
import com.localshops.repository.ProductRepository;
import com.localshops.repository.ShopRepository;
import com.localshops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public OrderResponse placeOrderWithRetry(CreateOrderRequest request, String customerEmail, String idempotencyKey) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return placeOrder(request, customerEmail, idempotencyKey);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.warn("Optimistic locking conflict on attempt {}/{}: {}", attempt, maxRetries, ex.getMessage());
                if (attempt == maxRetries) {
                    throw new BadRequestException("High demand for this item. Stock was modified by another customer. Please retry your order.");
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Order interrupted", ie);
                }
            }
        }
        throw new BadRequestException("Unable to complete order after retries due to concurrent checkout. Please try again.");
    }

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request, String customerEmail, String idempotencyKey) {
        User customer = userRepository.findByEmail(customerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerEmail));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new ForbiddenException("Only CUSTOMER users can place orders");
        }

        // Idempotency check: if key provided and already processed for this customer, return previous order
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey.trim());
            if (existingOrder.isPresent()) {
                log.info("Idempotent request matched for key: {}. Returning existing order id: {}", idempotencyKey, existingOrder.get().getId());
                return OrderResponse.fromEntity(existingOrder.get());
            }
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must have at least one item");
        }

        Shop singleShop = null;
        BigDecimal orderTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PLACED)
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null)
                .build();

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));

            // Rule: Orders must only contain products from one shop
            if (singleShop == null) {
                singleShop = product.getShop();
            } else if (!singleShop.getId().equals(product.getShop().getId())) {
                throw new BadRequestException("An order can only contain products from a single shop. Item '"
                        + product.getName() + "' belongs to another shop.");
            }

            // Check stock
            if (product.getStock() < itemReq.getQuantity()) {
                throw new BadRequestException("Insufficient stock for product '" + product.getName()
                        + "'. Requested: " + itemReq.getQuantity() + ", Available: " + product.getStock());
            }

            // Deduct stock (Hibernate checks @Version automatically during commit)
            product.setStock(product.getStock() - itemReq.getQuantity());
            productRepository.save(product);

            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            orderTotal = orderTotal.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .priceAtPurchase(product.getPrice())
                    .build();

            orderItems.add(orderItem);
        }

        order.setShop(singleShop);
        order.setTotal(orderTotal);
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus, String userEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        boolean isCustomer = order.getCustomer().getId().equals(user.getId());
        boolean isOwner = order.getShop().getOwner().getId().equals(user.getId());

        if (!isCustomer && !isOwner) {
            throw new ForbiddenException("You do not have permission to modify this order");
        }

        // Customer can only cancel when order is PLACED
        if (isCustomer && !isOwner) {
            if (newStatus != OrderStatus.CANCELLED) {
                throw new ForbiddenException("Customers can only cancel orders");
            }
            if (order.getStatus() != OrderStatus.PLACED) {
                throw new BadRequestException("Customer can only cancel orders in PLACED status. Current status: " + order.getStatus());
            }
        }

        // Validate state machine transition
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new BadRequestException("Invalid order transition from " + order.getStatus() + " to " + newStatus);
        }

        // If order cancelled, return inventory back to stock
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStock(product.getStock() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getCustomerOrders(String customerEmail, Pageable pageable) {
        User customer = userRepository.findByEmail(customerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerEmail));
        Page<Order> orders = orderRepository.findByCustomerId(customer.getId(), pageable);
        return PageResponse.from(orders.map(OrderResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getShopOrders(Long shopId, String ownerEmail, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + shopId));

        if (!shop.getOwner().getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ForbiddenException("You are not the owner of this shop");
        }

        Page<Order> orders = orderRepository.findByShopId(shopId, pageable);
        return PageResponse.from(orders.map(OrderResponse::fromEntity));
    }
}
