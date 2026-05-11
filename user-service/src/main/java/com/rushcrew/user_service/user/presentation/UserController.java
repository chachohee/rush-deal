package com.rushcrew.user_service.user.presentation;

import com.rushcrew.user_service.user.application.UserService;
import com.rushcrew.user_service.user.application.command.UserCreateCommand;
import com.rushcrew.user_service.user.application.command.UserUpdateCommand;
import com.rushcrew.user_service.user.application.command.VerifyPasswordCommand;
import com.rushcrew.user_service.user.application.result.UserCreateResult;
import com.rushcrew.user_service.user.application.result.UserInfoResult;
import com.rushcrew.user_service.user.application.result.UserResult;
import com.rushcrew.user_service.user.application.result.VerifyPasswordResult;
import com.rushcrew.user_service.user.presentation.dto.request.UserCreateRequest;
import com.rushcrew.user_service.user.presentation.dto.request.UserUpdateRequest;
import com.rushcrew.user_service.user.presentation.dto.request.VerifyPasswordRequest;
import com.rushcrew.user_service.user.presentation.dto.response.UserAllResponse;
import com.rushcrew.user_service.user.presentation.dto.response.UserCreateResponse;
import com.rushcrew.user_service.user.presentation.dto.response.UserInfoResponse;
import com.rushcrew.user_service.user.presentation.dto.response.UserResponse;
import com.rushcrew.user_service.user.presentation.dto.response.VerifyPasswordResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserCreateResponse> createUser(
        @Valid @RequestBody UserCreateRequest request
    ) {
        UserCreateCommand command = request.toCommand();

        UserCreateResult result = userService.createUser(command);

        URI location = URI.create("/api/v1/users/" + result.userId());

        return ResponseEntity.created(location).body(UserCreateResponse.fromResult(result));
    }

    @PostMapping("/verify-password")
    public ResponseEntity<VerifyPasswordResponse> verifyPassword(
        @Valid @RequestBody VerifyPasswordRequest request
    ) {
        VerifyPasswordCommand command = request.toCommand();

        VerifyPasswordResult result = userService.verifyPassword(command);

        return ResponseEntity.ok(VerifyPasswordResponse.fromResult(result));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<UserResponse> getUser(
        @RequestHeader("X-User-Id") Long userId
    ) {
        UserResult result = userService.getUser(userId);
        return ResponseEntity.ok(UserResponse.fromResult(result));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<Void> updateUser(
        @Valid @RequestBody UserUpdateRequest request,
        @RequestHeader("X-User-Id") Long userId
    ) {
        UserUpdateCommand command = request.toCommand(userId);

        userService.updateUser(command);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/internal/users/{userId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<UserInfoResponse> getUserById(
        @PathVariable Long userId
    ) {
        UserInfoResult result = userService.getUserById(userId);
        return ResponseEntity.ok(UserInfoResponse.fromResult(result));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<List<UserAllResponse>> getAllUsers() {
        List<UserAllResponse> response = userService.getAllUsers()
            .stream()
            .map(UserAllResponse::fromResult)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> changeRole(
        @PathVariable Long userId,
        @RequestParam String role
    ) {
        userService.changeRole(userId, role);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{userId}/block")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId) {
        userService.blockUser(userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{userId}/unblock")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId) {
        userService.unblockUser(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> deleteUser(
        @PathVariable Long userId,
        @RequestHeader("X-User-Id") Long adminId
    ) {
        userService.deleteUser(userId, adminId);
        return ResponseEntity.ok().build();
    }
}
