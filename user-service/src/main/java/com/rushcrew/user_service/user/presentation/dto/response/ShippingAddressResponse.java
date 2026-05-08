package com.rushcrew.user_service.user.presentation.dto.response;

import com.rushcrew.user_service.user.application.result.ShippingAddressResult;

public record ShippingAddressResponse(
    Long addressId,
    String recipientName,
    String recipientPhone,
    String zipCode,
    String addressBase,
    String addressDetail,
    String deliveryMessage,
    boolean isDefault
) {
    public static ShippingAddressResponse from(ShippingAddressResult result) {
        return new ShippingAddressResponse(
            result.addressId(),
            result.recipientName(),
            result.recipientPhone(),
            result.zipCode(),
            result.addressBase(),
            result.addressDetail(),
            result.deliveryMessage(),
            result.isDefault()
        );
    }
}
