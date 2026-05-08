package com.rushcrew.user_service.user.application;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.user_service.user.application.command.CreateShippingAddressCommand;
import com.rushcrew.user_service.user.application.command.UpdateShippingAddressCommand;
import com.rushcrew.user_service.user.application.result.ShippingAddressResult;
import com.rushcrew.user_service.user.domain.entity.ShippingAddress;
import com.rushcrew.user_service.user.domain.error.UserErrorCode;
import com.rushcrew.user_service.user.domain.repository.ShippingAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;

    public List<ShippingAddressResult> getAddresses(Long userId) {
        return shippingAddressRepository.findAllByUserId(userId)
            .stream()
            .map(ShippingAddressResult::from)
            .toList();
    }

    @Transactional
    public ShippingAddressResult createAddress(CreateShippingAddressCommand command) {
        ShippingAddress address = ShippingAddress.create(
            command.userId(),
            command.recipientName(),
            command.recipientPhone(),
            command.zipCode(),
            command.addressBase(),
            command.addressDetail(),
            command.deliveryMessage()
        );

        // 첫 번째 배송지는 자동으로 기본 배송지 설정
        boolean hasExisting = !shippingAddressRepository.findAllByUserId(command.userId()).isEmpty();
        if (!hasExisting) {
            address.setAsDefault();
        }

        return ShippingAddressResult.from(shippingAddressRepository.save(address));
    }

    @Transactional
    public ShippingAddressResult updateAddress(UpdateShippingAddressCommand command) {
        ShippingAddress address = shippingAddressRepository.findById(command.addressId())
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!address.getUserId().equals(command.userId())) {
            throw new BusinessException(UserErrorCode.ACCESS_DENIED);
        }

        address.update(
            command.recipientName(),
            command.recipientPhone(),
            command.zipCode(),
            command.addressBase(),
            command.addressDetail(),
            command.deliveryMessage()
        );

        return ShippingAddressResult.from(shippingAddressRepository.save(address));
    }

    @Transactional
    public void setDefault(Long userId, Long addressId) {
        ShippingAddress address = shippingAddressRepository.findById(addressId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(UserErrorCode.ACCESS_DENIED);
        }

        shippingAddressRepository.clearDefaultByUserId(userId);
        address.setAsDefault();
        shippingAddressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        ShippingAddress address = shippingAddressRepository.findById(addressId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(UserErrorCode.ACCESS_DENIED);
        }

        shippingAddressRepository.delete(address);
    }
}
