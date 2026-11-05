package com.localshops.controller;

import com.localshops.dto.ShopResponse;
import com.localshops.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/shops")
@RequiredArgsConstructor
@Tag(name = "Admin Shops", description = "Admin moderation for shop approval and rejection")
public class AdminShopController {

    private final ShopService shopService;

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a pending shop", description = "Admin-only endpoint to approve a shop")
    public ShopResponse approveShop(@PathVariable Long id) {
        return shopService.approveShop(id);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a pending shop", description = "Admin-only endpoint to reject a shop")
    public ShopResponse rejectShop(@PathVariable Long id) {
        return shopService.rejectShop(id);
    }
}
