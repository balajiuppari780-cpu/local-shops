package com.localshops.controller;

import com.localshops.dto.CreateProductRequest;
import com.localshops.dto.PageResponse;
import com.localshops.dto.ProductResponse;
import com.localshops.dto.UpdateProductRequest;
import com.localshops.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@Tag(name = "Products", description = "Endpoints for product search, management, and images")
public class ProductController {

    private final ProductService productService;

    @PostMapping("/api/shops/{shopId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Add a product to shop", description = "Shop owner adds a product to their approved shop")
    public ProductResponse createProduct(
            @PathVariable Long shopId,
            @Valid @RequestBody CreateProductRequest request,
            Principal principal
    ) {
        return productService.createProduct(shopId, request, principal.getName());
    }

    @PutMapping("/api/products/{id}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Update product", description = "Shop owner updates their product")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            Principal principal
    ) {
        return productService.updateProduct(id, request, principal.getName());
    }

    @DeleteMapping("/api/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Delete product", description = "Shop owner deletes their product")
    public void deleteProduct(
            @PathVariable Long id,
            Principal principal
    ) {
        productService.deleteProduct(id, principal.getName());
    }

    @GetMapping("/api/products/{id}")
    @Operation(summary = "Get product by ID", description = "Public endpoint to retrieve product details")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @GetMapping("/api/products")
    @Operation(summary = "Search products", description = "Public search with query, category, area, shopId, price range, inStock, and pagination")
    public PageResponse<ProductResponse> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return productService.searchProducts(q, category, area, shopId, minPrice, maxPrice, inStock, pageable);
    }

    @PostMapping(value = "/api/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @Operation(summary = "Upload product image", description = "Upload a JPG, PNG, or WEBP image for a product")
    public ProductResponse uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        return productService.uploadProductImage(id, file, principal.getName());
    }

    @GetMapping("/api/products/images/{filename:.+}")
    @Operation(summary = "Get product image file", description = "Public endpoint to serve product image files")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get("uploads", "products").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = MediaType.IMAGE_JPEG;
            if (filename.toLowerCase().endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (filename.toLowerCase().endsWith(".webp")) {
                mediaType = MediaType.parseMediaType("image/webp");
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400, public")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

