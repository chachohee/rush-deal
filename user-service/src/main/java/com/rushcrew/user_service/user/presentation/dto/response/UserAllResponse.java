package com.rushcrew.user_service.user.presentation.dto.response;

import com.rushcrew.user_service.user.application.result.UserAllResult;
import java.time.LocalDateTime;

public record UserAllResponse(
    Long userId,
    String email,
    String name,
    String role,
    boolean isBlocked,
    boolean isDeleted,
    LocalDateTime createdAt
) {
    public static UserAllResponse fromResult(UserAllResult result) {
        return new UserAllResponse(
            result.userId(),
            result.email(),
            result.name(),
            result.role(),
            result.isBlocked(),
            result.isDeleted(),
            result.createdAt()
        );
    }
}
