# 📊 Order Service 모니터링 가이드

> 메트릭 수집 및 시각화 접속 방법

---

## 🚀 접속 정보

### 1. Grafana (메트릭 시각화)

**접속 URL**: http://localhost:3000

**로그인 정보**:
- Username: `admin`
- Password: `admin`

**주요 기능**:
- 대시보드에서 메트릭 시각화
- Prometheus 데이터 소스 연결
- 커스텀 대시보드 생성

---

### 2. Prometheus (메트릭 수집)

**접속 URL**: http://localhost:9090

**주요 기능**:
- 메트릭 쿼리 (PromQL)
- 타겟 상태 확인
- 메트릭 탐색

**Order Service 메트릭 엔드포인트**: http://localhost:8050/actuator/prometheus

---

### 3. Zipkin (분산 추적)

**접속 URL**: http://localhost:9411

**주요 기능**:
- 분산 트레이싱
- 요청 추적
- 병목 지점 분석

---

## 📈 수집 중인 메트릭

### 1. Spring Boot 기본 메트릭

**HTTP 메트릭**
- `http_server_requests_seconds_count`: HTTP 요청 수
- `http_server_requests_seconds_sum`: HTTP 요청 총 시간
- `http_server_requests_seconds_max`: HTTP 요청 최대 시간

**JVM 메트릭**
- `jvm_memory_used_bytes`: JVM 메모리 사용량
- `jvm_memory_max_bytes`: JVM 메모리 최대값
- `jvm_gc_pause_seconds`: GC 일시정지 시간
- `jvm_threads_live`: 활성 스레드 수

**데이터베이스 메트릭**
- `hikari_connections_active`: 활성 커넥션 수
- `hikari_connections_idle`: 유휴 커넥션 수
- `hikari_connections_pending`: 대기 중인 커넥션 수

---

### 2. Order Service 커스텀 메트릭

#### Saga 메트릭

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `saga.execution.success` | Counter | 성공적으로 완료된 Saga 수 |
| `saga.execution.failure` | Counter | 실패한 Saga 수 |
| `saga.execution.timeout` | Counter | 타임아웃 처리된 Saga 수 |
| `saga.recovery.failure` | Counter | 타임아웃 Saga 복구 실패 수 |

**PromQL 예시**:
```promql
# Saga 성공률
rate(saga.execution.success[5m]) / (rate(saga.execution.success[5m]) + rate(saga.execution.failure[5m])) * 100

# Saga 타임아웃 수
rate(saga.execution.timeout[5m])
```

#### Outbox 메트릭

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `outbox.event.published` | Counter | Outbox 이벤트 발행 성공 수 |

**PromQL 예시**:
```promql
# 이벤트 발행 속도
rate(outbox.event.published[5m])
```

#### 캐시 메트릭

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `redis.cache.hit` | Counter | Redis 캐시 히트 수 |
| `redis.cache.miss` | Counter | Redis 캐시 미스 수 |
| `cache.warming.success` | Counter | Cache Warming 성공 건수 |
| `cache.warming.failure` | Counter | Cache Warming 실패 건수 |
| `cache.warming.duration` | Timer | Cache Warming 소요 시간 |
| `cache.hot_data.refresh.success` | Counter | Hot Data 갱신 성공 건수 |
| `cache.hot_data.refresh.duration` | Timer | Hot Data 갱신 소요 시간 |
| `cache.cold_data.cleanup.success` | Counter | Cold Data 정리 성공 건수 |
| `cache.cold_data.cleanup.duration` | Timer | Cold Data 정리 소요 시간 |

**PromQL 예시**:
```promql
# 캐시 Hit Rate
rate(redis.cache.hit[5m]) / (rate(redis.cache.hit[5m]) + rate(redis.cache.miss[5m])) * 100

# Cache Warming 성공률
rate(cache.warming.success[5m]) / (rate(cache.warming.success[5m]) + rate(cache.warming.failure[5m])) * 100
```

#### 주문 생성 메트릭

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `order.creation.duration` | Timer | 주문 생성 API 처리 소요 시간 |

**PromQL 예시**:
```promql
# 주문 생성 평균 시간
rate(order.creation.duration_seconds_sum[5m]) / rate(order.creation.duration_seconds_count[5m])

# 주문 생성 P95 시간
histogram_quantile(0.95, rate(order.creation.duration_seconds_bucket[5m]))
```

#### 캐시 동기화 메트릭

| 메트릭 이름 | 타입 | 설명 |
|------------|------|------|
| `cache.sync.success` | Counter | 캐시 동기화 성공 수 |
| `cache.sync.failure` | Counter | 캐시 동기화 실패 수 |
| `cache.sync.skipped` | Counter | 스킵된 캐시 동기화 수 |
| `cache.sync.duration` | Timer | 캐시 동기화 소요 시간 |

---

## 🔍 Grafana에서 확인하는 방법

### 1. 기본 대시보드 확인

1. **Grafana 접속**: http://localhost:3000
2. **로그인**: admin / admin
3. **대시보드 메뉴**: 왼쪽 메뉴에서 "Dashboards" 클릭
4. **대시보드 선택**: "RushDeal Services Overview" 또는 "Spring Boot Dashboard" 선택

### 2. 커스텀 메트릭 확인

**방법 1: Explore 메뉴 사용**
1. 왼쪽 메뉴에서 "Explore" 클릭
2. 데이터 소스 선택: "Prometheus"
3. 메트릭 이름 입력 (예: `saga.execution.success`)
4. "Run query" 클릭

**방법 2: 대시보드 생성**
1. "Dashboards" → "New Dashboard"
2. "Add visualization" 클릭
3. PromQL 쿼리 입력
4. 패널 설정 및 저장

---

## 📊 추천 대시보드 패널

### 1. Saga 메트릭 패널

**Saga 성공률 (Gauge)**
```promql
rate(saga.execution.success[5m]) / (rate(saga.execution.success[5m]) + rate(saga.execution.failure[5m])) * 100
```

**Saga 실행 수 (Graph)**
```promql
rate(saga.execution.success[5m])
rate(saga.execution.failure[5m])
rate(saga.execution.timeout[5m])
```

### 2. Outbox 메트릭 패널

**이벤트 발행 속도 (Graph)**
```promql
rate(outbox.event.published[5m])
```

**이벤트 발행 성공률 (Gauge)**
```promql
# PENDING 이벤트 수는 DB에서 직접 조회 필요
# Prometheus에서는 발행된 이벤트만 추적 가능
```

### 3. 캐시 메트릭 패널

**캐시 Hit Rate (Gauge)**
```promql
rate(redis.cache.hit[5m]) / (rate(redis.cache.hit[5m]) + rate(redis.cache.miss[5m])) * 100
```

**Cache Warming 성공률 (Gauge)**
```promql
rate(cache.warming.success[5m]) / (rate(cache.warming.success[5m]) + rate(cache.warming.failure[5m])) * 100
```

### 4. 주문 생성 성능 패널

**주문 생성 평균 시간 (Graph)**
```promql
rate(order.creation.duration_seconds_sum[5m]) / rate(order.creation.duration_seconds_count[5m])
```

**주문 생성 P95 시간 (Graph)**
```promql
histogram_quantile(0.95, rate(order.creation.duration_seconds_bucket[5m]))
```

**주문 생성 P99 시간 (Graph)**
```promql
histogram_quantile(0.99, rate(order.creation.duration_seconds_bucket[5m]))
```

### 5. JVM 메트릭 패널

**메모리 사용률 (Graph)**
```promql
jvm_memory_used_bytes{application="order-service"} / jvm_memory_max_bytes{application="order-service"} * 100
```

**GC 일시정지 시간 (Graph)**
```promql
rate(jvm_gc_pause_seconds_sum[5m])
```

**활성 스레드 수 (Graph)**
```promql
jvm_threads_live{application="order-service"}
```

---

## 🔧 Prometheus에서 직접 확인

### 1. 타겟 상태 확인

**접속**: http://localhost:9090/targets

**확인 사항**:
- `order-service:8050` 상태가 "UP"인지 확인
- 스크랩 간격: 15초
- 마지막 스크랩 시간 확인

### 2. 메트릭 탐색

**접속**: http://localhost:9090/graph

**메트릭 검색**:
- `saga.execution.success` 입력
- "Execute" 클릭
- 그래프 또는 테이블로 확인

### 3. PromQL 쿼리 예시

**Saga 성공률**
```promql
rate(saga.execution.success[5m]) / (rate(saga.execution.success[5m]) + rate(saga.execution.failure[5m])) * 100
```

**이벤트 발행 속도**
```promql
rate(outbox.event.published[5m])
```

**캐시 Hit Rate**
```promql
rate(redis.cache.hit[5m]) / (rate(redis.cache.hit[5m]) + rate(redis.cache.miss[5m])) * 100
```

**주문 생성 평균 시간**
```promql
rate(order.creation.duration_seconds_sum[5m]) / rate(order.creation.duration_seconds_count[5m])
```

---

## 🐛 문제 해결

### 1. Grafana에 메트릭이 안 보이는 경우

**확인 사항**:
1. Prometheus 데이터 소스 연결 확인
   - Grafana → Configuration → Data Sources
   - Prometheus URL: http://prometheus:9090 (Docker 내부) 또는 http://localhost:9090

2. Order Service 메트릭 엔드포인트 확인
   - http://localhost:8050/actuator/prometheus 접속
   - 메트릭이 출력되는지 확인

3. Prometheus 타겟 상태 확인
   - http://localhost:9090/targets
   - order-service 상태가 "UP"인지 확인

### 2. 커스텀 메트릭이 안 보이는 경우

**확인 사항**:
1. Order Service가 실행 중인지 확인
2. CustomMetrics가 실제로 호출되는지 로그 확인
3. Prometheus에서 메트릭 이름 검색
   - http://localhost:9090/graph
   - 메트릭 이름 입력하여 확인

### 3. Docker 환경에서 접속이 안 되는 경우

**확인 사항**:
1. 모니터링 컨테이너 실행 확인
   ```bash
   docker-compose -f docker-compose-monitor.yml up -d
   ```

2. 포트 확인
   ```bash
   docker ps | grep -E "grafana|prometheus|zipkin"
   ```

3. 네트워크 확인
   ```bash
   docker network ls | grep rushdeal
   ```

---

## 📝 대시보드 생성 가이드

### 1. Order Service 전용 대시보드 생성

1. **Grafana 접속**: http://localhost:3000
2. **대시보드 생성**: Dashboards → New Dashboard
3. **패널 추가**: Add visualization
4. **데이터 소스 선택**: Prometheus
5. **PromQL 쿼리 입력**: 위의 추천 쿼리 사용
6. **패널 설정**: 제목, 단위, 범례 등 설정
7. **저장**: 대시보드 이름 지정 후 저장

### 2. 추천 대시보드 구성

**Row 1: Saga 메트릭**
- Saga 성공률 (Gauge)
- Saga 실행 수 (Graph)
- Saga 타임아웃 수 (Graph)

**Row 2: Outbox 메트릭**
- 이벤트 발행 속도 (Graph)
- 이벤트 발행 성공률 (Gauge)

**Row 3: 캐시 메트릭**
- 캐시 Hit Rate (Gauge)
- Cache Warming 성공률 (Gauge)
- Cache Warming 소요 시간 (Graph)

**Row 4: 주문 생성 성능**
- 주문 생성 평균 시간 (Graph)
- 주문 생성 P95/P99 시간 (Graph)

**Row 5: JVM 메트릭**
- 메모리 사용률 (Graph)
- GC 일시정지 시간 (Graph)
- 활성 스레드 수 (Graph)

---

## 🎯 주요 모니터링 지표

### 실시간 확인해야 할 지표

1. **Saga 성공률**: 95% 이상 유지
2. **이벤트 발행 성공률**: 99% 이상 유지
3. **캐시 Hit Rate**: 85% 이상 유지
4. **주문 생성 P95 시간**: 5초 이하 유지
5. **JVM 메모리 사용률**: 80% 이하 유지

### 알림 설정 권장 사항

1. **Saga 실패율 > 5%**: 즉시 알림
2. **이벤트 발행 실패**: 즉시 알림
3. **캐시 Hit Rate < 70%**: 경고 알림
4. **주문 생성 P95 > 5초**: 경고 알림
5. **JVM 메모리 > 90%**: 즉시 알림

---

**작성일**: 2026-01-12  
**작성자**: 차초희
