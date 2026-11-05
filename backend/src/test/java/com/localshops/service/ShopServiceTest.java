package com.localshops.service;

import com.localshops.dto.CreateShopRequest;
import com.localshops.dto.ShopResponse;
import com.localshops.exception.ForbiddenException;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.model.User;
import com.localshops.repository.ShopRepository;
import com.localshops.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ShopService shopService;

    @Test
    @DisplayName("Should register shop in PENDING status for SHOP_OWNER")
    void shouldRegisterShop() {
        User owner = User.builder()
                .id(1L)
                .email("owner@example.com")
                .role(Role.SHOP_OWNER)
                .name("Anand")
                .build();

        CreateShopRequest request = CreateShopRequest.builder()
                .name("Anand Provisions")
                .description("Fresh daily groceries")
                .area("Indiranagar")
                .build();

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(shopRepository.existsByOwnerId(1L)).thenReturn(false);
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> {
            Shop shop = invocation.getArgument(0);
            shop.setId(10L);
            return shop;
        });

        ShopResponse response = shopService.registerShop(request, "owner@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Anand Provisions");
        assertThat(response.getStatus()).isEqualTo(ShopStatus.PENDING);
    }

    @Test
    @DisplayName("Should reject shop registration if user is not SHOP_OWNER")
    void shouldRejectWhenNotShopOwner() {
        User customer = User.builder()
                .id(2L)
                .email("customer@example.com")
                .role(Role.CUSTOMER)
                .build();

        CreateShopRequest request = CreateShopRequest.builder()
                .name("My Shop")
                .area("Koramangala")
                .build();

        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> shopService.registerShop(request, "customer@example.com"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only users with role SHOP_OWNER can register a shop");
    }

    @Test
    @DisplayName("Should approve shop successfully")
    void shouldApproveShop() {
        Shop shop = Shop.builder()
                .id(10L)
                .name("Daily Needs")
                .status(ShopStatus.PENDING)
                .build();

        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));

        ShopResponse response = shopService.approveShop(10L);

        assertThat(response.getStatus()).isEqualTo(ShopStatus.APPROVED);
    }
}
