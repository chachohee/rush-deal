package com.rushcrew.user_service.user.application;

import com.rushcrew.user_service.user.application.command.UserCreateCommand;
import com.rushcrew.user_service.user.application.command.UserUpdateCommand;
import com.rushcrew.user_service.user.application.command.VerifyPasswordCommand;
import com.rushcrew.user_service.user.application.result.UserAllResult;
import com.rushcrew.user_service.user.application.result.UserCreateResult;
import com.rushcrew.user_service.user.application.result.UserInfoResult;
import com.rushcrew.user_service.user.application.result.UserResult;
import com.rushcrew.user_service.user.application.result.VerifyPasswordResult;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.user_service.user.domain.entity.User;
import com.rushcrew.user_service.user.domain.enums.UserRole;
import com.rushcrew.user_service.user.domain.error.UserErrorCode;
import com.rushcrew.user_service.user.domain.repository.UserRepository;
import com.rushcrew.user_service.user.domain.service.UserValidator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserValidator userValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserCreateResult createUser(UserCreateCommand command) {
        userValidator.validateEmailUniqueness(command.email());

        UserRole userRole = UserRole.of(command.role());

        String encodedPassword = passwordEncoder.encode(command.password());

        User user = User.create(
            command.email(),
            encodedPassword,
            command.name(),
            userRole
        );

        User savedUser = userRepository.save(user);

        return new UserCreateResult(
            savedUser.getUserId(),
            savedUser.getEmail(),
            savedUser.getName(),
            savedUser.getRole().name()
        );
    }

        public VerifyPasswordResult verifyPassword(VerifyPasswordCommand command) {
        User user = userRepository.getByEmail(command.email());

        if (user.isDeleted()) throw new BusinessException(UserErrorCode.DELETED_USER);
        if (user.isBlocked()) throw new BusinessException(UserErrorCode.BLOCKED_USER);

        userValidator.validatePassword(user, command.password());

        return new VerifyPasswordResult(
            user.getUserId(),
            user.getEmail(),
            user.getName(),
            user.getRole().name()
        );
    }

    public UserResult getUser(Long userId) {
        User user = userRepository.getById(userId);

        return new UserResult(
            user.getUserId(),
            user.getEmail(),
            user.getName()
        );
    }

    public UserInfoResult getUserById(Long userId) {
        User user = userRepository.getById(userId);

        return new UserInfoResult(
            user.getUserId(),
            user.getName(),
            user.getRole().name()
        );
    }

    @Transactional
    public void updateUser(UserUpdateCommand command) {
        User user = userRepository.getById(command.userId());

        String encodedPassword = passwordEncoder.encode(command.password());

        user.updateUser(encodedPassword, command.name());
    }

    public List<UserAllResult> getAllUsers() {
        return userRepository.getAll()
            .stream()
            .map(UserAllResult::fromDomain)
            .toList();
    }

    @Transactional
    public void changeRole(Long targetUserId, String role) {
        User user = userRepository.getById(targetUserId);
        user.changeRole(UserRole.of(role));
    }

    @Transactional
    public void blockUser(Long targetUserId) {
        User user = userRepository.getById(targetUserId);
        user.block();
    }

    @Transactional
    public void unblockUser(Long targetUserId) {
        User user = userRepository.getById(targetUserId);
        user.unblock();
    }

    @Transactional
    public void deleteUser(Long targetUserId, Long adminId) {
        User user = userRepository.getById(targetUserId);
        user.softDelete(adminId);
    }
}
