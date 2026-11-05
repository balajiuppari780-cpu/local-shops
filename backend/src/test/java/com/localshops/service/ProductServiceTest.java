package com.localshops.service;

import com.localshops.dto.CreateProductRequest;
import com.localshops.dto.ProductResponse;
import com.localshops.dto.UpdateProductRequest;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.ForbiddenException;
import com.localshops.model.Product;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.model.User;
import com.localshops.repository.ProductRepository;
import com.localshops.repository.ShopRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Should create product for APPROVED shop by its owner")
    void shouldCreateProduct() {
        User owner = User.builder().id(1L).email("owner@example.com").role(Role.SHOP_OWNER).build();
        Shop shop = Shop.builder().id(5L).owner(owner).status(ShopStatus.APPROVED).name("Green Grocery").build();

        CreateProductRequest request = CreateProductRequest.builder()
                .name("Basmati Rice 5kg")
                .description("Premium aged basmati rice")
                .price(new BigDecimal("450.00"))
                .stock(20)
                .category("Grains")
                .build();

        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(100L);
            p.setVersion(0L);
            return p;
        });

        ProductResponse response = productService.createProduct(5L, request, "owner@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getName()).isEqualTo("Basmati Rice 5kg");
        assertThat(response.getPrice()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("Should throw BadRequestException when shop is not APPROVED")
    void shouldThrowWhenShopNotApproved() {
        User owner = User.builder().id(1L).email("owner@example.com").build();
        Shop shop = Shop.builder().id(5L).owner(owner).status(ShopStatus.PENDING).build();

        CreateProductRequest request = CreateProductRequest.builder()
                .name("Milk 1L")
                .price(new BigDecimal("30.00"))
                .stock(10)
                .category("Dairy")
                .build();

        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> productService.createProduct(5L, request, "owner@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Products can only be added to APPROVED shops");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-owner tries to update product")
    void shouldThrowWhenNonOwnerUpdates() {
        User owner = User.builder().id(1L).email("owner@example.com").build();
        Shop shop = Shop.builder().id(5L).owner(owner).build();
        Product product = Product.builder().id(20L).shop(shop).name("Item").build();

        UpdateProductRequest update = UpdateProductRequest.builder()
                .name("Updated Item")
                .price(new BigDecimal("100.00"))
                .stock(5)
                .category("Misc")
                .build();

        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProduct(20L, update, "imposter@example.com"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not have permission to update this product");
    }

    @Test
    @DisplayName("Should delete product when owner requests")
    void shouldDeleteProduct() {
        User owner = User.builder().id(1L).email("owner@example.com").build();
        Shop shop = Shop.builder().id(5L).owner(owner).build();
        Product product = Product.builder().id(20L).shop(shop).build();

        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        productService.deleteProduct(20L, "owner@example.com");

        verify(productRepository).delete(product);
    }
}

