package com.localshops.service;

import com.localshops.dto.CreateShopRequest;
import com.localshops.dto.PageResponse;
import com.localshops.dto.ShopResponse;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.ForbiddenException;
import com.localshops.exception.ResourceNotFoundException;
import com.localshops.model.Role;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.model.User;
import com.localshops.repository.ShopRepository;
import com.localshops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShopResponse registerShop(CreateShopRequest request, String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + ownerEmail));

        if (owner.getRole() != Role.SHOP_OWNER) {
            throw new ForbiddenException("Only users with role SHOP_OWNER can register a shop");
        }

        if (shopRepository.existsByOwnerId(owner.getId())) {
            throw new BadRequestException("Shop owner already has a registered shop");
        }

        Shop shop = Shop.builder()
                .owner(owner)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .area(request.getArea().trim())
                .status(ShopStatus.PENDING)
                .build();

        Shop savedShop = shopRepository.save(shop);
        return ShopResponse.fromEntity(savedShop);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShopResponse> getApprovedShops(String area, Pageable pageable) {
        Page<Shop> page;
        if (area != null && !area.isBlank()) {
            page = shopRepository.findByStatusAndAreaIgnoreCase(ShopStatus.APPROVED, area.trim(), pageable);
        } else {
            page = shopRepository.findByStatus(ShopStatus.APPROVED, pageable);
        }
        return PageResponse.from(page.map(ShopResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public ShopResponse getShopById(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + id));
        return ShopResponse.fromEntity(shop);
    }

    @Transactional
    public ShopResponse approveShop(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + id));
        shop.setStatus(ShopStatus.APPROVED);
        return ShopResponse.fromEntity(shop);
    }

    @Transactional
    public ShopResponse rejectShop(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + id));
        shop.setStatus(ShopStatus.REJECTED);
        return ShopResponse.fromEntity(shop);
    }
}
