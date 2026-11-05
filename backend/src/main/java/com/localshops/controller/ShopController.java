package com.localshops.controller;

import com.localshops.dto.CreateShopRequest;
import com.localshops.dto.PageResponse;
import com.localshops.dto.ShopResponse;
import com.localshops.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Tag(name = "Shops", description = "Shop registration and public shop discovery")
public class ShopController {

    private final ShopService shopService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Register a new shop", description = "Allows a shop owner to register a shop in PENDING status")
    public ShopResponse registerShop(
            @Valid @RequestBody CreateShopRequest request,
            Principal principal
    ) {
        return shopService.registerShop(request, principal.getName());
    }

    @GetMapping
    @Operation(summary = "List approved shops", description = "Public list of approved shops with area filtering and pagination")
    public PageResponse<ShopResponse> getApprovedShops(
            @RequestParam(required = false) String area,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return shopService.getApprovedShops(area, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get shop details by ID", description = "Public shop detail lookup")
    public ShopResponse getShopById(@PathVariable Long id) {
        return shopService.getShopById(id);
    }
}
