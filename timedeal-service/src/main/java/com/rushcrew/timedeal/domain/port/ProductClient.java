package com.rushcrew.timedeal.domain.port;

import com.rushcrew.timedeal.application.model.ProductInfo;
import com.rushcrew.timedeal.application.model.ProductSearchInfo;
import java.util.UUID;

public interface ProductClient {

    /**
     * 생성할 타임딜에서 판매할 상품의 옵션ID들과 sellerId, price 등을 가져오는 메서드
     */
    ProductInfo getProductItemIds(UUID productId);

    /**
     * 검색 인덱싱에 필요한 상품의 표시용 정보 (이름, 회사명, 카테고리)
     */
    ProductSearchInfo getProductSearchInfo(UUID productId);
}
