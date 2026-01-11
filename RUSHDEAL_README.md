# ⏰ Rush Deal
> 트래픽 집중 상황을 고려한 MSA 기반 타임딜 이커머스 플랫폼
> ![rushdeal.png](rushdeal.png)

## 목차
- [프로젝트 소개](#-프로젝트-소개)
- [프로젝트 목표](#-프로젝트-목표)
- [로컬 실행 방법](#-로컬-실행-방법)
- [주요 기능](#-주요-기능)
- [팀원 및 역할](#-팀원-및-역할)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [ERD](#-erd)
- [프로젝트 구조](#-프로젝트-구조)


## 📌 프로젝트 소개
### 개요
한정된 시간, 수량 안에서 주문이 집중되는 타임딜 커머스 환경의 
MSA(Microservices Architecture) 기반 커머스 플랫폼입니다.

트래픽 집중 상황에서 발생할 수 있는 동시성, 정합성, 서비스 병목 문제 등을 
해결하기 위해 동시성 제어, 비동기 이벤트 기반 서비스 연동, Redis · Kafka · 모니터링 도구를 활용한
트래픽 처리 구조를 설계하고 구현했습니다.



## 🚩 프로젝트 목표
### 대규모 트래픽 대응
- MSA 기반 서비스 분리로 확장성 고려
- Redis, Kafka로 대규모 트래픽 안정적 처리
- 동시성 문제 없는 서비스
### 배포 및 운영
- Docker 기반 서비스별 실행 환경 통일
- docker-compose를 통한 인프라 및 서비스 일괄 실행
### 모니터링 시스템 구축
- Prometheus + Grafana를 활용한 메트릭 수집 및 시각화
- Zipkin을 통한 분산 트레이싱 및 병목 지점 분석



## ▶️ 로컬 실행 방법
1. 환경 변수 설정
2. Docker 컨테이너 실행
    ```
    docker-compose up -d
    ```


## **🔑** 주요 기능
### 프로젝트 주요 기능
#### Saga·Outbox 패턴 기반의 고신뢰 주문 결제 시스템
- 주문-재고-포인트 서비스 간 분산 트랜잭션을 Saga 패턴으로 관리하고, 장애 발생 시 자동 보상 트랜잭션 수행
- **이벤트 발행 성공률 99.9% 달성**
  - Outbox 패턴과 FOR UPDATE SKIP LOCKED를 조합해 DB 트랜잭션과 메시지 발행 간의 원자성 확보
- 멱등성 키 기반의 재시도 전략을 통해 네트워크 장애 및 Kafka 일시 장애 상황에서도 **95% 이상의 복구율** 구현

#### Redis Sorted Set 기반의 고가용성 대기열 시스템
- 대규모 트래픽으로 인한 DB 병목을 해결하기 위해 Redis Sorted Set 기반 대기열/활성열 구조 도입
- 전용 Executor(Thread Pool)와 CompletableFuture를 적용해 공용 스레드 풀 간섭 제거 및 비동기 처리 성능 최적화
- User Index Key 관리로 1인 1토큰 원칙 보장 및 중복 진입 원천 차단

#### 낙관적 락 + Redis 선제어를 결합한 타임딜 재고 관리
- Optimistic Lock으로 재고 수정 시 정합성 유지, Redis 선제어로 불필요한 DB 요청 사전 차단
- Redis Sorted Set 기반의 스케줄러 큐를 통해 타임딜 시작/종료 자동화

#### CQRS 및 2-Tier 캐싱을 통한 조회 성능 최적화
- CQRS 적용 & 2-Tier 전략으로 주문 조회 성능 **50배 향상 (500ms -> 10ms)**
- Cache Hit Rate 85~90% 유지로 시스템 처리 효율 극대화

#### PortOne 연동 결제 서비스
- PortOne 기반 결제 파이프라인 구축으로 결제 준비/완료/취소 구현
- 결제 취소 시 Kafka 이벤트 발행으로 비동기 도메인 연동

#### 사용자 및 포인트 시스템
- 포인트 적립/차감을 Kafka 이벤트로 연동해 도메인 간 결합도 최소화
- Redisson 분산 락으로 다중 요청 상황에서도 포인트 데이터 무결성 보장


## 🧑‍🤝‍🧑 팀원 및 역할
| 이름                                     | 담당 업무             |
|----------------------------------------|-------------------|
| [변영재(팀장)](https://github.com/bbangjae) | 인증 및 인가, 사용자, 포인트 |
| [김민수](https://github.com/Doritosch)    | 결제, 모니터링          |
| [민송경](https://github.com/miiiiiin)     | 대기열, 배포           |
| [유민아](https://github.com/minahYu)      | 상품, 타임딜, 재고       |
| [차초희](https://github.com/chachohee)    | 주문                |


## 🛠 기술 스택
### Back-End
- Java 21
- Spring Boot 3.5.8
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Spring Kafka
- PostgreSQL
- Spring Cloud Gateway
- Eureka
- OpenFeign

### Infrastructure
- AWS
  - ECS
  - RDS (PostgreSQL)
  - Elastic Cache (Redis)
  - MSK (Kafka)
  - ECR
- Docker & Docker Compose
- Github Actions

### Monitoring
- Prometheus
- Grafana
- Zipkin

## 🔁 Flowchart
![flowchart.png](docs/image/flowchart.png)

## 🏗 시스템 아키텍처
![erd.png](docs/image/architecture.png)

## 🗄 ERD
![erd.png](docs/image/rushdeal_erd.png)

## 📂 프로젝트 구조
```
rush-deal
├── api-gateway         # 요청 라우팅 및 인증 처리
├── auth-service        # 인증 및 토큰 관리
├── discovery-service   # 서비스 디스커버리 (Eureka)
├── order-service       # 주문 처리
├── payment-service     # 결제 처리
├── product-service     # 상품 및 옵션 관리
├── timedeal-service    # 타임딜 및 재고 도메인
├── user-service        # 사용자 및 포인트 관리
├── queue-service       # 대기열 서비스
├── monitoring          # 모니터링
├── docker-compose.yml
├── docker-compose-monitoring.yml
└── README.md
```
