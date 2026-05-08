package com.rushcrew.user_service.user.application.result;

import com.rushcrew.user_service.user.domain.entity.ShippingAddress;

public record ShippingAddressResult(
    Long addressId,
    Long userId,
    String recipientName,
    String recipientPhone,
    String zipCode,
    String addressBase,
    String addressDetail,
    String deliveryMessage,
    boolean isDefault
) {
    public static ShippingAddressResult from(ShippingAddress entity) {
        return new ShippingAddressResult(
            entity.getAddressId(),
            entity.getUserId(),
            entity.getRecipientName(),
            entity.getRecipientPhone(),
            entity.getZipCode(),
            entity.getAddressBase(),
            entity.getAddressDetail(),
            entity.getDeliveryMessage(),
            entity.isDefault()
        );
    }
}
