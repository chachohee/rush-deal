package com.rushcrew.user_service.user.presentation.dto.request;

import com.rushcrew.user_service.user.application.command.UpdateShippingAddressCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateShippingAddressRequest(

    @NotBlank(message = "수령인 이름은 필수입니다")
    @Size(max = 50)
    String recipientName,

    @NotBlank(message = "휴대폰 번호는 필수입니다")
    @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대폰 번호 형식이 아닙니다 (예: 01012345678)")
    String recipientPhone,

    @NotBlank(message = "우편번호는 필수입니다")
    @Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 5자리 숫자여야 합니다")
    String zipCode,

    @NotBlank(message = "기본 주소는 필수입니다")
    @Size(max = 255)
    String addressBase,

    @NotBlank(message = "상세 주소는 필수입니다")
    @Size(max = 255)
    String addressDetail,

    @Size(max = 100)
    String deliveryMessage
) {
    public UpdateShippingAddressCommand toCommand(Long userId, Long addressId) {
        return new UpdateShippingAddressCommand(
            userId, addressId, recipientName, recipientPhone, zipCode, addressBase, addressDetail, deliveryMessage
        );
    }
}
