package com.rushcrew.auth_service.auth.infrastructure.external;

import com.rushcrew.auth_service.auth.application.client.UserClient;
import com.rushcrew.auth_service.auth.application.command.LoginCommand;
import com.rushcrew.auth_service.auth.application.command.SignUpCommand;
import com.rushcrew.auth_service.auth.application.result.UserCreateResult;
import com.rushcrew.auth_service.auth.application.result.UserInfoResult;
import com.rushcrew.auth_service.auth.application.result.VerifyPasswordResult;
import com.rushcrew.auth_service.auth.domain.exception.AuthErrorCode;
import com.rushcrew.auth_service.auth.infrastructure.external.dto.UserCreateRequest;
import com.rushcrew.auth_service.auth.infrastructure.external.dto.UserCreateResponse;
import com.rushcrew.auth_service.auth.infrastructure.external.dto.UserInfoResponse;
import com.rushcrew.auth_service.auth.infrastructure.external.dto.VerifyPasswordRequest;
import com.rushcrew.auth_service.auth.infrastructure.external.dto.VerifyPasswordResponse;
import com.rushcrew.common.exception.BusinessException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserClientImpl implements UserClient {

    private final UserFeignClient userFeignClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "createUserFallback")
    @Retry(name = "userService")
    @Override
    public UserCreateResult createUser(SignUpCommand command) {
        UserCreateRequest request = UserCreateRequest.fromCommand(command);

        try {
            UserCreateResponse response = userFeignClient.createUser(request);
            return response.toResult();
        } catch (FeignException.Conflict e) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
        } catch (FeignException.BadRequest | FeignException.Unauthorized e) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (FeignException e) {
            log.warn("User service error during createUser: status={}, message={}", e.status(), e.getMessage());
            throw e;
        }
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "verifyPasswordFallback")
    @Retry(name = "userService")
    @Override
    public VerifyPasswordResult verifyPassword(LoginCommand command) {
        VerifyPasswordRequest request = VerifyPasswordRequest.fromCommand(
            command
        );

        try {
            VerifyPasswordResponse response = userFeignClient.verifyPassword(
                request
            );
            return response.toResult();
        } catch (
            FeignException.BadRequest
            | FeignException.Unauthorized
            | FeignException.NotFound e
        ) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (FeignException e) {
            log.warn("User service error during verifyPassword: status={}, message={}", e.status(), e.getMessage());
            throw e;
        }
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByIdFallback")
    @Retry(name = "userService")
    @Override
    public UserInfoResult getUserById(Long userId) {
        try {
            UserInfoResponse response = userFeignClient.getUserById(userId);
            return response.toResult();
        } catch (FeignException.NotFound e) {
            throw new BusinessException(AuthErrorCode.USER_NOT_FOUND);
        } catch (FeignException e) {
            log.warn(
                "User service error during getUserById: status={}, message={}",
                e.status(),
                e.getMessage()
            );
            throw e;
        }
    }

    private UserCreateResult createUserFallback(
        SignUpCommand command,
        Exception e
    ) {
        if (e instanceof BusinessException be) throw be;
        log.error("User service unavailable during createUser. email={}, cause={}", command.email(), e.getMessage(), e);
        throw new BusinessException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    private VerifyPasswordResult verifyPasswordFallback(
        LoginCommand command,
        Exception e
    ) {
        if (e instanceof BusinessException be) throw be;
        log.error("User service unavailable during verifyPassword. email={}, cause={}", command.email(), e.getMessage(), e);
        throw new BusinessException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    private UserInfoResult getUserByIdFallback(Long userId, Exception e) {
        if (e instanceof BusinessException be) throw be;
        log.error("User service unavailable during getUserById. userId={}, cause={}", userId, e.getMessage(), e);
        throw new BusinessException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
    }
}
