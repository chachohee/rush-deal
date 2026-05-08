package com.rushcrew.user_service.user.application.command;

public record CreateShippingAddressCommand(
    Long userId,
    String recipientName,
    String recipientPhone,
    String zipCode,
    String addressBase,
    String addressDetail,
    String deliveryMessage
) {}
