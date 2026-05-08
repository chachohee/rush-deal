package com.rushcrew.user_service.user.domain.entity;

import com.rushcrew.user_service.user.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "p_shipping_address", schema = "user_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 5)
    private String zipCode;

    @Column(nullable = false, length = 255)
    private String addressBase;

    @Column(nullable = false, length = 255)
    private String addressDetail;

    @Column(length = 100)
    private String deliveryMessage;

    @Column(nullable = false)
    private boolean isDefault;

    public static ShippingAddress create(Long userId, String recipientName, String recipientPhone,
            String zipCode, String addressBase, String addressDetail, String deliveryMessage) {
        ShippingAddress address = new ShippingAddress();
        address.userId = userId;
        address.recipientName = recipientName;
        address.recipientPhone = recipientPhone;
        address.zipCode = zipCode;
        address.addressBase = addressBase;
        address.addressDetail = addressDetail;
        address.deliveryMessage = deliveryMessage;
        address.isDefault = false;
        return address;
    }

    public void update(String recipientName, String recipientPhone,
            String zipCode, String addressBase, String addressDetail, String deliveryMessage) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.addressBase = addressBase;
        this.addressDetail = addressDetail;
        this.deliveryMessage = deliveryMessage;
    }

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }
}
