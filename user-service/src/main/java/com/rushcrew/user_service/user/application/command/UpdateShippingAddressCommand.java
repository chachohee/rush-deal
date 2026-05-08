package com.rushcrew.user_service.user.application.command;

public record UpdateShippingAddressCommand(
    Long userId,
    Long addressId,
    String recipientName,
    String recipientPhone,
    String zipCode,
    String addressBase,
    String addressDetail,
    String deliveryMessage
) {}
