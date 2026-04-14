package com.rushcrew.order_service.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@JsonDeserialize(builder = ShippingInfo.ShippingInfoBuilder.class)
public class ShippingInfo {

	@Column(nullable = false, length = 50)
	private String recipientName;

	@Column(nullable = false, length = 20)
	private String recipientPhone;

	@Column(nullable = false, length = 10)
	private String zipCode;

	@Column(nullable = false, length = 255)
	private String addressBase;

	@Column(nullable = false, length = 255)
	private String addressDetail;

	@Column(length = 100)
	private String deliveryMessage;

	public static ShippingInfo create(String recipientName,
		String recipientPhone,
		String zipCode,
		String addressBase,
		String addressDetail,
		String deliveryMessage) {
		ShippingInfo info = new ShippingInfo(recipientName, recipientPhone, zipCode, addressBase, addressDetail, deliveryMessage);
		info.validate(); // 생성 시 검증 수행
		return info;
	}

	@JsonPOJOBuilder(withPrefix = "")
	public static class ShippingInfoBuilder {
	}

	public void validate() {
		if (recipientName == null || recipientName.isBlank()) {
			throw new IllegalArgumentException("수령인 이름은 필수입니다.");
		}

		if (recipientPhone == null || !recipientPhone.matches("^01[0-9]{8,9}$")) {
			throw new IllegalArgumentException("올바른 휴대폰 번호 형식이 아닙니다.");
		}

		if (zipCode == null || !zipCode.matches("^[0-9]{5}$")) {
			throw new IllegalArgumentException("우편번호는 5자리 숫자여야 합니다.");
		}

		if (addressBase == null || addressBase.isBlank()) {
			throw new IllegalArgumentException("기본 주소는 필수입니다.");
		}

		if (addressDetail == null || addressDetail.isBlank()) {
			throw new IllegalArgumentException("상세 주소는 필수입니다.");
		}
	}

}
