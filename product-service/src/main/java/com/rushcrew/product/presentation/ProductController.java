package com.rushcrew.product.presentation;

import com.rushcrew.product.application.ProductFilter;
import com.rushcrew.product.application.command.CreateProductCommand;
import com.rushcrew.product.application.command.UpdateProductCommand;
import com.rushcrew.product.application.result.CreateProductResult;
import com.rushcrew.product.application.result.ProductDetailResult;
import com.rushcrew.product.application.result.ProductResult;
import com.rushcrew.product.application.result.UpdateProductResult;
import com.rushcrew.product.application.service.ProductService;
import com.rushcrew.product.global.security.model.UserDetailsImpl;
import com.rushcrew.product.infrastructure.storage.ImageUploadService;
import com.rushcrew.product.presentation.dto.request.CreateProductRequest;
import com.rushcrew.product.presentation.dto.request.UpdateProductRequest;
import com.rushcrew.product.presentation.dto.response.CreateProductResponse;
import com.rushcrew.product.presentation.dto.response.ProductDetailResponse;
import com.rushcrew.product.presentation.dto.response.ProductResponse;
import com.rushcrew.product.presentation.dto.response.UpdateProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ImageUploadService imageUploadService;

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<ImageUploadResponse> uploadImage(
        @RequestPart("file") MultipartFile file
    ) {
        String url = imageUploadService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImageUploadResponse(url));
    }

    public record ImageUploadResponse(String imageUrl) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<CreateProductResponse> createProduct(
        @Valid @RequestBody CreateProductRequest request,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        CreateProductCommand command = request.toCommand();
        CreateProductResult result =
            productService.createProduct(principal.userId(), principal.role(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateProductResponse.from(result));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<UpdateProductResponse> updateProduct(
        @PathVariable UUID productId,
        @Valid @RequestBody UpdateProductRequest request,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        UpdateProductCommand command = request.toCommand();
        UpdateProductResult result =
            productService.updateProduct(principal.userId(), principal.role(), productId, command);
        return ResponseEntity.ok(UpdateProductResponse.from(result));
    }

    @PostMapping("/{productId}/disable")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<Void> disableProduct(
        @PathVariable UUID productId,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        productService.disableProduct(principal.userId(), principal.role(), productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/enable")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<Void> enableProduct(
        @PathVariable UUID productId,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        productService.enableProduct(principal.userId(), principal.role(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<Void> deleteProduct(
        @PathVariable UUID productId,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        productService.deleteProduct(principal.userId(), principal.role(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
        @RequestParam(required = false) List<String> category,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @SortDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        ProductFilter productFilter = ProductFilter.of(category, minPrice, maxPrice);
        Page<ProductResult> resultList = productService.getProducts(productFilter, pageable);
        Page<ProductResponse> response = resultList.map(ProductResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(
        @PathVariable UUID productId
    ) {
        ProductDetailResult result = productService.getProductDetail(productId);
        ProductDetailResponse response = ProductDetailResponse.from(result);
        return ResponseEntity.ok(response);
    }
}
