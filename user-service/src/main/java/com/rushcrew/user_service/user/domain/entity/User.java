package com.rushcrew.user_service.user.domain.entity;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.user_service.user.domain.common.BaseEntity;
import com.rushcrew.user_service.user.domain.enums.UserRole;
import com.rushcrew.user_service.user.domain.error.UserErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "p_user", schema = "user_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean isBlocked = false;

    public static User create(String email, String password, String name, UserRole role) {
        User user = new User();

        validateUserInfo(email, password, name);

        user.email = email;
        user.password = password;
        user.name = name;
        user.role = role;

        return user;
    }

    public static void validateUserInfo(String email, String password, String name) {
        if (email == null) {
            throw new BusinessException(UserErrorCode.INVALID_USER_INFO);
        }

        if (password == null) {
            throw new BusinessException(UserErrorCode.INVALID_USER_INFO);
        }

        if (name == null) {
            throw new BusinessException(UserErrorCode.INVALID_USER_INFO);
        }
    }

    public void updateUser(String newPassword, String newName) {
        changePassword(newPassword);
        changeName(newName);
    }

    private void changePassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(UserErrorCode.INVALID_USER_INFO);
        }
        this.password = newPassword;
    }

    private void changeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(UserErrorCode.INVALID_USER_INFO);
        }
        this.name = name;
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    public void block() {
        this.isBlocked = true;
    }

    public void unblock() {
        this.isBlocked = false;
    }
}
