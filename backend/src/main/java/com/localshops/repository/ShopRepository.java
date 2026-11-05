package com.localshops.repository;

import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    Page<Shop> findByStatusAndAreaIgnoreCase(ShopStatus status, String area, Pageable pageable);

    Optional<Shop> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
