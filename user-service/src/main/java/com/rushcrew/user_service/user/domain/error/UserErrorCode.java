package com.rushcrew.user_service.user.domain.error;

import com.rushcrew.common.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "USER-003", "비밀번호가 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "USER-004", "접근 권한이 없습니다."),
    INVALID_USER_ROLE(HttpStatus.BAD_REQUEST, "USER-005", "유효하지 않은 권한입니다."),
    INVALID_USER_INFO(HttpStatus.BAD_REQUEST, "USER-006", "유효하지 않은 사용자 정보입니다."),
    BLOCKED_USER(HttpStatus.FORBIDDEN, "USER-007", "정지된 계정입니다."),
    DELETED_USER(HttpStatus.FORBIDDEN, "USER-008", "삭제된 계정입니다.");

    private final HttpStatus httpStatus;
    private final String name;
    private final String message;
}
