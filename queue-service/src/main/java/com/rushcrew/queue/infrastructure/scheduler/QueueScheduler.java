    package com.rushcrew.queue.infrastructure.scheduler;

    import com.rushcrew.common.exception.BusinessException;
    import com.rushcrew.queue.application.port.in.QueuePort;
    import com.rushcrew.queue.domain.entity.QueuePolicy;
    import com.rushcrew.queue.domain.repository.QueuePolicyRepository;
    import com.rushcrew.queue.domain.vo.TrafficSetting;
    import java.time.LocalDateTime;
    import java.util.List;
    import java.util.UUID;
    import java.util.concurrent.TimeUnit;
    import lombok.extern.slf4j.Slf4j;
    import org.redisson.api.RLock;
    import org.redisson.api.RedissonClient;
    import org.springframework.data.redis.core.RedisTemplate;
    import org.springframework.scheduling.annotation.Scheduled;
    import org.springframework.stereotype.Component;
    import java.util.concurrent.CopyOnWriteArrayList;

    @Slf4j
    @Component
    public class QueueScheduler {
        private final QueuePolicyRepository queuePolicyRepository;
        private final QueuePort queueService;
        private final RedisTemplate<String, String> redisTemplate;
        private final RedissonClient redissonClient;

        // DB 부하를 줄이기 위한 인메모리 캐시 (스레드 세이프)
        private final List<QueuePolicy> cachedPolicies = new CopyOnWriteArrayList<>();

        // 마지막 실행 시간 기록 Redis 키
        private static final String LAST_RUN_KEY = "queue:scheduler:last_run:%s";
        // 락 키
        private static final String LOCK_KEY = "lock:scheduler:product:%s";

        public QueueScheduler(QueuePolicyRepository queuePolicyRepository, QueuePort queueService,
            RedisTemplate<String, String> redisTemplate, RedissonClient redissonClient) {
            this.queuePolicyRepository = queuePolicyRepository;
            this.queueService = queueService;
            this.redisTemplate = redisTemplate;
            this.redissonClient = redissonClient;
        }

        /**
         * [Refresher] 정책 캐시 갱신 (주기: 1분)
         * 역할: DB에서 "현재 진행 중"이거나 "곧 시작(1분 내)할" 정책을 미리 가져옴
         * 목적: DB 조회 횟수를 획기적으로 줄임 (1분 1회)
         */
        @Scheduled(fixedRate = 60000)
        public void refreshPolicies() {
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneMinuteLater = now.plusMinutes(1);

                // 현재 유효한(진행 중인) 타임딜 정책 조회
                List<QueuePolicy> allActivePolicies = queuePolicyRepository.findAllActivePolicies(now,
                    oneMinuteLater);

                // 새 리스트로 교체 (원자적 작업)
                List<QueuePolicy> newPolicies = new CopyOnWriteArrayList<>(allActivePolicies);

                // 캐시 교체 (CopyOnWriteArrayList는 참조 교체 시 스레드 세이프)
                cachedPolicies.clear();
                cachedPolicies.addAll(newPolicies);
                log.info("[Scheduler:Refresher] 정책 캐시 갱신 완료. (로드된 정책 수: {})", allActivePolicies.size());
            } catch (Exception e) {
                // SQLException 터지면 스케줄링이 전부 중단될 수 있으므로 기존 캐시를 유지하여 서비스가 계속 운영되도록 함
                // 기존 캐시로 계속 운영 (장애 시에도 서비스 유지. 스케줄러 영구 중단 방지)
                log.error("[Scheduler:Refresher] 정책 캐시 갱신 실패 - 기존 캐시 유지", e);
            }
        }

        /**
         * 타임딜 활성화 스케줄러
         * 동적 스케줄러 (Ticker)
         * 1초(queueGap의 최소값) 주기로 실행하며 각 정책의 queueGap을 체크합니다.
         * 진행 중인 타임딜을 찾아 각자의 queueGap 주기에 맞춰 활성화를 요청함
         * DB I/O 없음 (위에 refreshPolicies에서 1분마다 캐시 갱신해줌)
         */
        @Scheduled(fixedDelay = 1000)
        public void scheduleActivation() {
            if (cachedPolicies.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // 병렬 처리로 성능 개선 (상품별 독립 락이므로 안전)
            cachedPolicies.parallelStream()
                .filter(policy -> isWithinRunningTime(policy, now))
                .forEach(this::processPolicyWithLock);
        }

        /**
         * 대기열 정책에서 대기열 진입 시간 확인 로직
         */
        private boolean isWithinRunningTime(QueuePolicy policy, LocalDateTime now) {
            // "곧 시작(1분 내)할" 정책도 refreshPolicies 메서드에서 미리 가져왔으므로,
            // 실제로 지금 시간이 시작 시간을 지났는지 메모리 상에서 체크
            LocalDateTime startTime = policy.getTimePeriod().getStartTime();
            LocalDateTime endTime = policy.getTimePeriod().getEndTime();
            return (now.isEqual(startTime) || now.isAfter(startTime)) && now.isBefore(endTime);
        }

        /**
         * 각 정책별로 활성화 로직 수행 (병렬 처리 가능)
         * 각 상품별로 락을 걸고 실행 (다중 서버 돌리는 상황에서 중복 실행 방지)
         * 락 획득 실패 시 즉시 반환 (다음 스케줄에서 재시도)
         */
        private void processPolicyWithLock(QueuePolicy queuePolicy) {
            UUID productId = queuePolicy.getProductId();

            String lockKey = getLockKey(productId);
            // 락 키를 상품별로 생성하여 병렬 처리
            RLock lock = redissonClient.getLock(lockKey);

            try {
                // tryLock(대기시간, 점유시간, 단위)
                // waitTime = 0: 락을 못 잡으면 즉시 포기 (다른 서버가 하고 있겠거니 생각. 대기 X, 다음 턴에서)
                // Scale-out(다중 서버) 상황에서 서버 A가 실행 중이면, 서버 B와 C는 1초 뒤(다음 스케줄링)에 다시 시도하면 되므로 굳이 기다릴 필요X
                // (Thread Blocking 방지)

                // leaseTime = 3초: 3초 뒤엔 락 자동 반납 (서버가 죽었을 때 데드락 방지)
                // 스케줄러 주기가 1초이므로 leaseTime은 1~3초 정도로 짧게 설정
                boolean isLocked = lock.tryLock(0, 3, TimeUnit.SECONDS);

                if (!isLocked) {
                    // 이미 다른 서버가 실행 중임 -> 패스
                    log.info("[Scheduler] 락 획득 패스 - 이미 다른 서버가 실행 중");
                    return;
                }

                log.info("[Scheduler] 락 획득 성공 - 상품 활성화 진행");

                // 락 획득 성공
                TrafficSetting setting = queuePolicy.getTrafficSetting();
                // 대기열 -> 활성열로 이동 주기
                int queueGap = setting.getQueueGap();

                // Redis에 기록된 마지막 실행 시간 체크 (실행 가능 여부 검사)
                if (!canExecute(productId, queueGap)) {
                    return;
                }

                // 실행 시간 먼저 갱신 (중복 실행 방지)
                long executionTime = System.currentTimeMillis();
                updateLastExecutionTime(productId, executionTime);

                // 대기열 -> 활성열 이동 요청
                activateTokens(productId, executionTime, setting);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[Scheduler] 락 획득 중 인터럽트 발생", e);
            } catch (Exception e) {
                log.error("[Scheduler] 상품({}) 활성화 중 에러 발생", productId, e);
            } finally {
                // 내가 건 락인 경우에만 해제
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        /**
         * 대기열 토큰 활성열 이동 처리 (토큰 활성화)
         * 토큰 활성화 실패 시 마지막 실행 시간(lastExecutionTime) 롤백
         */
        private void activateTokens(UUID productId, long executionTime, TrafficSetting setting) {
            try {
                boolean success = queueService.activateTokens(productId, setting);

                if (!success) {
                    // activateTokens 실패 시 실행 시간 롤백
                    log.warn("[Scheduler] 상품({}) 활성화 실패 - 실행 시간 롤백", productId);
                    // 예외를 다시 던지지 않음 (다음 스케줄에서 재시도하도록)
                    rollbackLastExecutionTime(productId, executionTime, setting.getQueueGap());
                    return;
                }
                log.info("[Scheduler] 상품({}) 활성화 완료", productId);
            } catch (Exception e) {
                // 실제 에러 (Redis 장애, DB 장애 등)
                log.error("[Scheduler] 상품({}) 활성화 중 예외 발생 - 실행 시간 롤백", productId, e);
                rollbackLastExecutionTime(productId, executionTime, setting.getQueueGap());
            }
        }

        /**
         * activateTokens 실패 시 실행 시간 롤백
         * queueGap 시간만큼 이전으로 되돌려 다음 스케줄에서 재시도 가능하도록 함
         */
        private void rollbackLastExecutionTime(UUID productId, long executionTime, Integer queueGap) {
            try {
                String lastRunKey = getLastRunKey(productId);
                // queueGap 시간만큼 되돌려서 다음 스케줄에서 재시도 가능하도록
                long rollbackTime = executionTime - (queueGap * 1000L);
                redisTemplate.opsForValue().set(
                    lastRunKey,
                    String.valueOf(rollbackTime)
                );
                log.info("[Scheduler] 상품({}) 실행 시간 롤백 완료", productId);
            } catch (BusinessException e) {
                log.error("[Scheduler][CRITICAL] 상품({}) 실행 시간 롤백 실패 - 수동 개입 필요", productId, e);
            }
        }

        /**
         * 해당 상품의 스케줄러 실행 자격이 있는지 검사
         * 조건: (현재시간 - 마지막실행시간) >= queueGap
         */
        private boolean canExecute(UUID productId, int queueGap) {
            try {
                String lastRunKey = getLastRunKey(productId);
                String lastRunTimeStr = redisTemplate.opsForValue().get(lastRunKey);

                // 첫 시작에는 true
                if (lastRunTimeStr == null) return true;

                long lastRunTime = Long.parseLong(lastRunTimeStr);
                long currentTime = System.currentTimeMillis();
                long diffMillis = currentTime - lastRunTime;

                // 밀리초 단위로 비교 (초 단위보다 정밀)
                return diffMillis >= (queueGap * 1000L);
            } catch (Exception e) {
                log.error("[Scheduler] Redis 조회 실패 (상품: {}) - 다음 스케줄링에서 재시도", productId, e);
                // Redis 조회 실패 시 false 반환 (다음 스케줄에서 재시도)
                return false;
            }
        }

        /**
         * 마지막 실행 시간 갱신
         */
        private void updateLastExecutionTime(UUID productId, long executionTime) {
            String lastRunKey = getLastRunKey(productId);
            redisTemplate.opsForValue().set(lastRunKey, String.valueOf(executionTime));
        }

        private String getLastRunKey(UUID productId) {
            return String.format(LAST_RUN_KEY, productId);
        }

        private String getLockKey(UUID productId) {
            return String.format(LOCK_KEY, productId);
        }
    }
