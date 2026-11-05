package com.localshops.dto;

import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopResponse {

    private Long id;
    private Long ownerId;
    private String ownerName;
    private String name;
    private String description;
    private String area;
    private ShopStatus status;
    private Instant createdAt;

    public static ShopResponse fromEntity(Shop shop) {
        return ShopResponse.builder()
                .id(shop.getId())
                .ownerId(shop.getOwner() != null ? shop.getOwner().getId() : null)
                .ownerName(shop.getOwner() != null ? shop.getOwner().getName() : null)
                .name(shop.getName())
                .description(shop.getDescription())
                .area(shop.getArea())
                .status(shop.getStatus())
                .createdAt(shop.getCreatedAt())
                .build();
    }
}

