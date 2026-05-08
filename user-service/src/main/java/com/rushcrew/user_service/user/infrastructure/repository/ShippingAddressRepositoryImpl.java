package com.rushcrew.user_service.user.infrastructure.repository;

import com.rushcrew.user_service.user.domain.entity.ShippingAddress;
import com.rushcrew.user_service.user.domain.repository.ShippingAddressRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingAddressRepositoryImpl implements ShippingAddressRepository {

    private final ShippingAddressJpaRepository jpaRepository;

    @Override
    public ShippingAddress save(ShippingAddress address) {
        return jpaRepository.save(address);
    }

    @Override
    public List<ShippingAddress> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Optional<ShippingAddress> findById(Long addressId) {
        return jpaRepository.findById(addressId);
    }

    @Override
    public Optional<ShippingAddress> findDefaultByUserId(Long userId) {
        return jpaRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId);
    }

    @Override
    public void clearDefaultByUserId(Long userId) {
        jpaRepository.clearDefaultByUserId(userId);
    }

    @Override
    public void delete(ShippingAddress address) {
        address.softDelete(address.getUserId());
        jpaRepository.save(address);
    }
}
