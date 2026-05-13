package com.rushcrew.product.domain.exception;

import com.rushcrew.common.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
    OPTION_LIST_EMPTY(HttpStatus.BAD_REQUEST, "PO-001", "최소 1개 이상의 옵션이 필요합니다."),
    NOT_FOUND_OPTION(HttpStatus.NOT_FOUND, "PO-002", "존재하지 않는 옵션입니다."),

    // 상품
    NOT_FOUND_PRODUCT(HttpStatus.NOT_FOUND, "P-001", "존재하지 않는 상품입니다."),
    REQUIRED_SELLER_ID(HttpStatus.BAD_REQUEST, "P-002", "MASTER로 상품 생성 시 sellerId는 필수입니다."),

    // 이미지
    IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "P-IMG-001", "업로드할 이미지가 비어있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "P-IMG-002", "이미지 크기는 5MB를 초과할 수 없습니다."),
    IMAGE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "P-IMG-003", "지원하지 않는 이미지 형식입니다. (jpeg/png/webp/gif)"),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P-IMG-004", "이미지 업로드에 실패했습니다."),

    ;

    private final HttpStatus httpStatus;
    private final String name;
    private final String message;
}
