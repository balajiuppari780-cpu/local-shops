package com.localshops.service;

import com.localshops.dto.CreateOrderRequest;
import com.localshops.dto.OrderItemRequest;
import com.localshops.model.Product;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.model.User;
import com.localshops.repository.OrderRepository;
import com.localshops.repository.ProductRepository;
import com.localshops.repository.ShopRepository;
import com.localshops.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Concurrency test: multiple concurrent customers try to buy the last remaining item; only 1 succeeds")
    void testConcurrentOrdersForLastItem() throws InterruptedException {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);

        // Setup owner and shop
        User owner = userRepository.save(User.builder()
                .name("Shop Owner")
                .email("owner_" + suffix + "@example.com")
                .passwordHash("pass")
                .role(Role.SHOP_OWNER)
                .build());

        Shop shop = shopRepository.save(Shop.builder()
                .name("Super Mart " + suffix)
                .area("Whitefield")
                .status(ShopStatus.APPROVED)
                .owner(owner)
                .build());

        // Product with stock = 1
        Product product = productRepository.save(Product.builder()
                .name("Rare Organic Honey")
                .price(new BigDecimal("500.00"))
                .stock(1)
                .category("Groceries")
                .shop(shop)
                .build());

        int numberOfCustomers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfCustomers);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(numberOfCustomers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Pre-create 5 customer users
        for (int i = 1; i <= numberOfCustomers; i++) {
            final String email = "customer" + i + "_" + suffix + "@example.com";
            userRepository.save(User.builder()
                    .name("Customer " + i)
                    .email(email)
                    .passwordHash("pass")
                    .role(Role.CUSTOMER)
                    .build());

            executor.submit(() -> {
                try {
                    startSignal.await(); // wait for gun shot to fire all simultaneously
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .items(List.of(OrderItemRequest.builder()
                                    .productId(product.getId())
                                    .quantity(1)
                                    .build()))
                            .build();

                    orderService.placeOrder(request, email, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Fire all threads at once
        startSignal.countDown();
        doneSignal.await();
        executor.shutdown();

        // Exactly 1 order must have succeeded, and remaining 4 must have failed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(numberOfCustomers - 1);

        // Verify remaining stock in DB is exactly 0 (no negative stock, overselling prevented!)
        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getStock()).isEqualTo(0);
    }
}
