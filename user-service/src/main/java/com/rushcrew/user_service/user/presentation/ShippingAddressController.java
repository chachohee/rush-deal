package com.rushcrew.user_service.user.presentation;

import com.rushcrew.user_service.user.application.ShippingAddressService;
import com.rushcrew.user_service.user.application.result.ShippingAddressResult;
import com.rushcrew.user_service.user.presentation.dto.request.CreateShippingAddressRequest;
import com.rushcrew.user_service.user.presentation.dto.request.UpdateShippingAddressRequest;
import com.rushcrew.user_service.user.presentation.dto.response.ShippingAddressResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @GetMapping
    public ResponseEntity<List<ShippingAddressResponse>> getAddresses(
        @RequestHeader("X-User-Id") Long userId
    ) {
        List<ShippingAddressResponse> response = shippingAddressService.getAddresses(userId)
            .stream()
            .map(ShippingAddressResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ShippingAddressResponse> createAddress(
        @Valid @RequestBody CreateShippingAddressRequest request,
        @RequestHeader("X-User-Id") Long userId
    ) {
        ShippingAddressResult result = shippingAddressService.createAddress(request.toCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingAddressResponse.from(result));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ShippingAddressResponse> updateAddress(
        @PathVariable Long addressId,
        @Valid @RequestBody UpdateShippingAddressRequest request,
        @RequestHeader("X-User-Id") Long userId
    ) {
        ShippingAddressResult result = shippingAddressService.updateAddress(request.toCommand(userId, addressId));
        return ResponseEntity.ok(ShippingAddressResponse.from(result));
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<Void> setDefault(
        @PathVariable Long addressId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        shippingAddressService.setDefault(userId, addressId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
        @PathVariable Long addressId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        shippingAddressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
