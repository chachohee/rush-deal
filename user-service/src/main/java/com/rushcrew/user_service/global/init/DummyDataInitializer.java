// package com.rushcrew.user_service.global.init;
//
// import com.rushcrew.user_service.point.domain.entity.PointHistory;
// import com.rushcrew.user_service.point.domain.repository.PointHistoryRepository;
// import com.rushcrew.user_service.point.domain.vo.OrderId;
// import com.rushcrew.user_service.point.domain.vo.Point;
// import com.rushcrew.user_service.point.domain.vo.SagaId;
// import com.rushcrew.user_service.point.domain.vo.UserId;
// import com.rushcrew.user_service.user.domain.entity.User;
// import com.rushcrew.user_service.user.domain.enums.UserRole;
// import com.rushcrew.user_service.user.domain.repository.UserRepository;
// import java.util.UUID;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.stereotype.Component;
//
// @Slf4j
// @Component
// @RequiredArgsConstructor
// public class DummyDataInitializer implements CommandLineRunner {
//
//     private final UserRepository userRepository;
//     private final PasswordEncoder passwordEncoder;
//     private final PointHistoryRepository pointHistoryRepository;
//
//     @Override
//     public void run(String... args) {
//
//         if (!userRepository.getAll().isEmpty()) {
//             log.info("🚫 Dummy data already exists. Skipping initialization.");
//             return;
//         }
//
//         log.info("🚀 Initializing Dummy User & Point Data...");
//
//         User user = User.create(
//             "test@rushdeal.com",
//             passwordEncoder.encode("pass1234!"),
//             "테스트 유저",
//             UserRole.USER
//         );
//         User savedUser = userRepository.save(user);
//
//         PointHistory initialPoint = PointHistory.confirmEarn(
//             UserId.of(savedUser.getUserId()),
//             OrderId.of(UUID.randomUUID().toString()),
//             Point.of(10_000L),
//             Point.of(10_000L),
//             SagaId.of(UUID.randomUUID().toString())
//         );
//
//         pointHistoryRepository.save(initialPoint);
//
//         log.info("🎉 Dummy User created: userId={}, balance=10,000", savedUser.getUserId());
//     }
// }
