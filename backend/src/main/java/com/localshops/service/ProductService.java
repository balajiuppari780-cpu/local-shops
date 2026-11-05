package com.localshops.service;

import com.localshops.dto.CreateProductRequest;
import com.localshops.dto.PageResponse;
import com.localshops.dto.ProductResponse;
import com.localshops.dto.UpdateProductRequest;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.ForbiddenException;
import com.localshops.exception.ResourceNotFoundException;
import com.localshops.model.Product;
import com.localshops.model.Shop;
import com.localshops.model.ShopStatus;
import com.localshops.repository.ProductRepository;
import com.localshops.repository.ShopRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    private static final Path UPLOAD_DIR = Paths.get("uploads", "products");

    @Transactional
    public ProductResponse createProduct(Long shopId, CreateProductRequest request, String ownerEmail) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + shopId));

        if (!shop.getOwner().getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ForbiddenException("You are not the owner of this shop");
        }

        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BadRequestException("Products can only be added to APPROVED shops. Current status: " + shop.getStatus());
        }

        Product product = Product.builder()
                .shop(shop)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .price(request.getPrice())
                .stock(request.getStock())
                .category(request.getCategory().trim())
                .build();

        Product savedProduct = productRepository.save(product);
        return ProductResponse.fromEntity(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request, String ownerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getShop().getOwner().getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ForbiddenException("You do not have permission to update this product");
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory().trim());

        Product updatedProduct = productRepository.save(product);
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id, String ownerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getShop().getOwner().getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ForbiddenException("You do not have permission to delete this product");
        }

        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.fromEntity(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(
            String q,
            String category,
            String area,
            Long shopId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Pageable pageable
    ) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search query against name or description
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameMatch, descMatch));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (shopId != null) {
                predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            }

            if (area != null && !area.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("shop").get("area")), area.trim().toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (inStock != null) {
                if (inStock) {
                    predicates.add(cb.greaterThan(root.get("stock"), 0));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("stock"), 0));
                }
            }

            // Only show products from APPROVED shops
            predicates.add(cb.equal(root.get("shop").get("status"), ShopStatus.APPROVED));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> page = productRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(ProductResponse::fromEntity));
    }

    @Transactional
    public ProductResponse uploadProductImage(Long id, MultipartFile file, String ownerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getShop().getOwner().getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ForbiddenException("You do not have permission to modify this product");
        }

        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded image cannot be empty");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!extension.matches("\\.(jpg|jpeg|png|webp)")) {
            throw new BadRequestException("Only JPG, JPEG, PNG, or WEBP image formats are allowed");
        }

        try {
            if (!Files.exists(UPLOAD_DIR)) {
                Files.createDirectories(UPLOAD_DIR);
            }

            String filename = UUID.randomUUID() + extension;
            Path destination = UPLOAD_DIR.resolve(filename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            product.setImageUrl("/api/products/images/" + filename);
            Product updated = productRepository.save(product);
            return ProductResponse.fromEntity(updated);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store image file: " + e.getMessage(), e);
        }
    }
}

