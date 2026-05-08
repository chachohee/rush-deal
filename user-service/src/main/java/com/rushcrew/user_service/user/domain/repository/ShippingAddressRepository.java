package com.rushcrew.user_service.user.domain.repository;

import com.rushcrew.user_service.user.domain.entity.ShippingAddress;
import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository {

    ShippingAddress save(ShippingAddress address);

    List<ShippingAddress> findAllByUserId(Long userId);

    Optional<ShippingAddress> findById(Long addressId);

    Optional<ShippingAddress> findDefaultByUserId(Long userId);

    void clearDefaultByUserId(Long userId);

    void delete(ShippingAddress address);
}
