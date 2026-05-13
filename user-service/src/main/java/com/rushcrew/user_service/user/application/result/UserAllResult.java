package com.rushcrew.user_service.user.application.result;

import com.rushcrew.user_service.user.domain.entity.User;
import java.time.LocalDateTime;

public record UserAllResult(
    Long userId,
    String email,
    String name,
    String role,
    boolean isBlocked,
    boolean isDeleted,
    LocalDateTime createdAt
) {
    public static UserAllResult fromDomain(User user) {
        return new UserAllResult(
            user.getUserId(),
            user.getEmail(),
            user.getName(),
            user.getRole().name(),
            user.isBlocked(),
            user.isDeleted(),
            user.getCreatedAt()
        );
    }
}
