package com.rushcrew.user_service.user.infrastructure.repository;

import com.rushcrew.user_service.user.domain.entity.ShippingAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShippingAddressJpaRepository extends JpaRepository<ShippingAddress, Long> {

    List<ShippingAddress> findAllByUserIdAndDeletedAtIsNull(Long userId);

    Optional<ShippingAddress> findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE ShippingAddress s SET s.isDefault = false WHERE s.userId = :userId AND s.isDefault = true")
    void clearDefaultByUserId(@Param("userId") Long userId);
}
