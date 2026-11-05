package com.localshops.service;

import com.localshops.dto.CreateOrderRequest;
import com.localshops.dto.OrderItemRequest;
import com.localshops.dto.OrderResponse;
import com.localshops.exception.BadRequestException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Should successfully place order and reduce stock atomically")
    void shouldPlaceOrderAndReduceStock() {
        User customer = User.builder().id(1L).email("cust@example.com").role(Role.CUSTOMER).name("Cust").build();
        Shop shop = Shop.builder().id(2L).name("Shop 1").build();
        Product product = Product.builder()
                .id(10L)
                .shop(shop)
                .name("Wheat Flour 10kg")
                .price(new BigDecimal("350.00"))
                .stock(15)
                .version(1L)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(10L).quantity(2).build()))
                .build();

        when(userRepository.findByEmail("cust@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.placeOrder(request, "cust@example.com", "idemp-key-1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTotal()).isEqualByComparingTo("700.00");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(product.getStock()).isEqualTo(13); // 15 - 2 = 13
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Should return existing order on duplicate idempotency key without re-reducing stock")
    void shouldReturnExistingOnIdempotencyKey() {
        User customer = User.builder().id(1L).email("cust@example.com").role(Role.CUSTOMER).build();
        Order existingOrder = Order.builder()
                .id(99L)
                .customer(customer)
                .idempotencyKey("repeat-key")
                .total(new BigDecimal("500.00"))
                .status(OrderStatus.PLACED)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(10L).quantity(1).build()))
                .build();

        when(userRepository.findByEmail("cust@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(1L, "repeat-key")).thenReturn(Optional.of(existingOrder));

        OrderResponse response = orderService.placeOrder(request, "cust@example.com", "repeat-key");

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getIdempotencyKey()).isEqualTo("repeat-key");
    }

    @Test
    @DisplayName("Should reject order when stock is insufficient")
    void shouldRejectWhenStockInsufficient() {
        User customer = User.builder().id(1L).email("cust@example.com").role(Role.CUSTOMER).build();
        Shop shop = Shop.builder().id(2L).build();
        Product product = Product.builder().id(10L).shop(shop).name("Apples").price(new BigDecimal("100.00")).stock(2).build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(10L).quantity(5).build()))
                .build();

        when(userRepository.findByEmail("cust@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.placeOrder(request, "cust@example.com", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock for product 'Apples'");
    }

    @Test
    @DisplayName("Should reject order spanning multiple shops")
    void shouldRejectMultiShopOrder() {
        User customer = User.builder().id(1L).email("cust@example.com").role(Role.CUSTOMER).build();
        Shop shop1 = Shop.builder().id(1L).build();
        Shop shop2 = Shop.builder().id(2L).build();

        Product prod1 = Product.builder().id(10L).shop(shop1).name("Prod 1").price(new BigDecimal("50.00")).stock(10).build();
        Product prod2 = Product.builder().id(20L).shop(shop2).name("Prod 2").price(new BigDecimal("50.00")).stock(10).build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder().productId(10L).quantity(1).build(),
                        OrderItemRequest.builder().productId(20L).quantity(1).build()
                ))
                .build();

        when(userRepository.findByEmail("cust@example.com")).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(prod1));
        when(productRepository.findById(20L)).thenReturn(Optional.of(prod2));

        assertThatThrownBy(() -> orderService.placeOrder(request, "cust@example.com", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("single shop");
    }

    @Test
    @DisplayName("Should advance order status correctly: PLACED -> ACCEPTED -> PACKED -> DELIVERED")
    void shouldAdvanceStatus() {
        User owner = User.builder().id(5L).email("owner@example.com").build();
        Shop shop = Shop.builder().id(2L).owner(owner).build();
        User customer = User.builder().id(1L).email("cust@example.com").build();

        Order order = Order.builder().id(50L).customer(customer).shop(shop).status(OrderStatus.PLACED).build();

        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse res1 = orderService.updateOrderStatus(50L, OrderStatus.ACCEPTED, "owner@example.com");
        assertThat(res1.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        OrderResponse res2 = orderService.updateOrderStatus(50L, OrderStatus.PACKED, "owner@example.com");
        assertThat(res2.getStatus()).isEqualTo(OrderStatus.PACKED);

        OrderResponse res3 = orderService.updateOrderStatus(50L, OrderStatus.DELIVERED, "owner@example.com");
        assertThat(res3.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Should restore product stock when order is CANCELLED")
    void shouldRestoreStockOnCancellation() {
        User customer = User.builder().id(1L).email("cust@example.com").build();
        Shop shop = Shop.builder().id(2L).owner(User.builder().id(5L).build()).build();
        Product product = Product.builder().id(10L).stock(8).build();

        Order order = Order.builder().id(50L).customer(customer).shop(shop).status(OrderStatus.PLACED).build();
        OrderItem item = OrderItem.builder().order(order).product(product).quantity(4).priceAtPurchase(new BigDecimal("100.00")).build();
        order.setItems(List.of(item));

        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("cust@example.com")).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.updateOrderStatus(50L, OrderStatus.CANCELLED, "cust@example.com");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(12); // 8 + 4 = 12
        verify(productRepository).save(product);
    }
}
