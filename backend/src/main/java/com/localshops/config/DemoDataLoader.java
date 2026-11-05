package com.localshops.config;

import com.localshops.model.Product;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.model.User;
import com.localshops.repository.ProductRepository;
import com.localshops.repository.ShopRepository;
import com.localshops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already contains data. Skipping demo data seeding.");
            return;
        }

        log.info("Seeding initial demo data for local-shops...");

        // 1. Seed demo users
        User admin = userRepository.save(User.builder()
                .name("Platform Admin")
                .email("admin@localshops.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(Role.ADMIN)
                .build());

        User shopOwner = userRepository.save(User.builder()
                .name("Ramesh Sharma")
                .email("owner@localshops.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(Role.SHOP_OWNER)
                .build());

        User customer = userRepository.save(User.builder()
                .name("Priya Verma")
                .email("customer@localshops.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role(Role.CUSTOMER)
                .build());

        // 2. Seed approved shop
        Shop shop = shopRepository.save(Shop.builder()
                .name("Green Valley Provisions")
                .description("Neighborhood store for daily fresh groceries and essentials")
                .area("Indiranagar")
                .status(ShopStatus.APPROVED)
                .owner(shopOwner)
                .build());

        // 3. Seed catalog products
        List<Product> products = List.of(
                Product.builder()
                        .shop(shop)
                        .name("Royal Basmati Rice 5kg")
                        .description("Premium aged long-grain fragrant basmati rice")
                        .price(new BigDecimal("480.00"))
                        .stock(30)
                        .category("Grains")
                        .build(),
                Product.builder()
                        .shop(shop)
                        .name("Organic Whole Wheat Atta 5kg")
                        .description("Stone ground 100% whole wheat flour")
                        .price(new BigDecimal("260.00"))
                        .stock(25)
                        .category("Flour")
                        .build(),
                Product.builder()
                        .shop(shop)
                        .name("Cold Pressed Sunflower Oil 1L")
                        .description("Pure filtered sunflower cooking oil")
                        .price(new BigDecimal("165.00"))
                        .stock(40)
                        .category("Oils")
                        .build(),
                Product.builder()
                        .shop(shop)
                        .name("Unpolished Toor Dal 1kg")
                        .description("High-protein naturally grown yellow lentils")
                        .price(new BigDecimal("150.00"))
                        .stock(50)
                        .category("Pulses")
                        .build(),
                Product.builder()
                        .shop(shop)
                        .name("Farm Fresh Cow Milk 1L")
                        .description("Pasteurized fresh full-cream milk")
                        .price(new BigDecimal("36.00"))
                        .stock(20)
                        .category("Dairy")
                        .build()
        );

        productRepository.saveAll(products);

        log.info("Demo data seeding completed successfully!");
        log.info("Demo accounts: Admin: admin@localshops.com | Owner: owner@localshops.com | Customer: customer@localshops.com (Password: Password@123)");
    }
}
