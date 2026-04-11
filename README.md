# Coupon Service

대용량 트래픽 환경에서 선착순 쿠폰 발급을 처리하는 MSA 기반 쿠폰 서비스입니다.

## 아키텍처

```
couponService/
├── coupon-api        # WebFlux 기반 쿠폰 발급 API (Port: 8081)
├── coupon-consumer   # Kafka 이벤트 소비 및 실제 쿠폰 발급 처리 (Port: 8082)
├── coupon-admin      # 쿠폰 캠페인 관리 (Port: 8083)
├── coupon-domain     # 공유 도메인 모델 및 비즈니스 로직
└── product-stub      # 상품 서비스 Mock (Port: 8084)
```

## 핵심 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.3 |
| Reactive | Spring WebFlux |
| ORM | Spring Data JPA, QueryDSL 5.1.0 |
| Message Queue | Apache Kafka |
| Cache / 분산 제어 | Redis 7 |
| Database | MySQL 8.0 |
| Build | Gradle |

## 쿠폰 캠페인 유형

| 유형 | 설명 |
|------|------|
| `FIRST_COME` | 선착순 수량 제한 발급 |
| `CODE` | 쿠폰 코드 기반 발급 |
| `OPEN` | 수량 무제한 발급 |
| `MANUAL` | 관리자 직접 발급 |

---

## 전체 발급 플로우

### FIRST_COME (선착순)

```
Client
  │  POST /api/issue {userId, campaignId}
  ▼
coupon-api (WebFlux)
  │  ① Redis Cache에서 캠페인 정보 조회 (CouponCacheService)
  │  ② availableIssueableCoupon() — 상태·기간·수량 1차 검증
  │  ③ Redis Lua 스크립트 실행
  │     - SISMEMBER: 중복 발급 체크
  │     - INCR + 수량 비교: 원자적 수량 선점
  │  ④ SUCCESS → DB에 CouponIssueRequest + CouponEventLog 저장
  │              (Schedulers.boundedElastic — JPA 블로킹 격리)
  │  ⑤ Kafka 토픽 발행: first-coupon-issue-requested
  ▼
응답 즉시 반환 (비동기)
  │
  ▼
coupon-consumer
  └── 4-step 처리 (아래 참조)
```

### OPEN (무제한)

```
coupon-api
  │  ① Redis Cache 조회 + 검증
  │  ② Redis Lua 스크립트 — SISMEMBER 중복 체크만 수행 (수량 선점 없음)
  │  ③ DB 저장 → Kafka 토픽 발행: open-coupon-issue-requested
  ▼
coupon-consumer
  └── 4-step 처리 (아래 참조)
```

---

## Kafka 토픽 목록

| 토픽 | 방향 | 설명 |
|------|------|------|
| `first-coupon-issue-requested` | API → Consumer | 선착순 쿠폰 발급 요청 |
| `open-coupon-issue-requested` | API → Consumer | 무제한 쿠폰 발급 요청 |
| `coupon-issue-retry-step1` | Consumer → Consumer | STEP1 실패 재시도 |
| `coupon-issue-retry-step2` | Consumer → Consumer | STEP2 실패 재시도 |
| `coupon-issue-retry-step3` | Consumer → Consumer | STEP3 실패 재시도 |
| `coupon-issue-retry-step4` | Consumer → Consumer | STEP4 실패 재시도 |
| `coupon-issue-complete` | Consumer → (downstream) | 발급 완료 이벤트 |
| `coupon-issue-all-fail` | Consumer → (downstream) | retryCount ≥ 3 최종 실패 |

---

## Consumer 처리 (4-step)

메시지 하나가 도착하면 아래 4단계를 순서대로 실행합니다.
각 단계에서 예외가 발생하면 해당 단계의 retry 토픽으로 재발행하고 처리를 중단합니다.

```
Kafka 메시지 수신
       │
       ▼
  [STEP 1] 이벤트 중복 검증
       │  - CouponEventLog에 SUCCESS 레코드 존재 → 이미 처리 완료, 중복 → 즉시 종료 (retry 없음)
       │  - CouponEventLog 자체 없음 → 유효하지 않은 요청 → retry-step1 발행
       │  - 중복 아니면 IssueRequest 상태를 PROCESSING으로 변경
       │  실패 시 → coupon-issue-retry-step1 발행
       │
       ▼
  [STEP 2] UserCoupon 생성 + Outbox 저장 (같은 트랜잭션)
       │  - UserCoupon 저장
       │  - OutboxEvent(PENDING) 저장
       │  - checkAlreadyIssuedUserCoupon() — DB 레벨 중복 방어
       │  실패 시 → coupon-issue-retry-step2 발행
       │
       ▼
  [STEP 3] IssueRequest 상태 업데이트 + CouponEventLog 기록
       │  - IssueRequest.status = ISSUED
       │  - CouponEventLog 새 레코드 INSERT (status=SUCCESS, 이전 로그에서 eventType/payload 복사)
       │  - CouponCampaign.issuedQuantity 증가
       │  실패 시 → coupon-issue-retry-step3 발행
       │
       ▼
  [STEP 4] 완료 이벤트 발행
       │  - coupon-issue-complete 토픽으로 발행
       │  실패 시 → coupon-issue-retry-step4 발행
       │            (OutboxScheduler가 at-least-once 보완)
       │
       ▼
     완료
```

### retry 처리 흐름

```
retry 토픽 수신
       │
       ▼
  UpdateRetry() 호출
       ├── retryCount < 3  → retryCount 증가
       │                     CouponEventLog 새 레코드 INSERT (status=RETRYING, errorMessage 포함)
       │                     해당 STEP부터 재실행
       │
       └── retryCount >= 3 → allFailed() 호출
                              IssueRequest.status = FAILED_FATAL
                              CouponEventLog 새 레코드 INSERT (status=FAILED, errorMessage 포함)
                              coupon-issue-all-fail 토픽 발행
```

---

## 재시도 및 복구 체계

### 단계별 retry 토픽 분리

실패한 단계부터만 재실행하므로 이미 성공한 단계를 반복하지 않습니다.
각 retry 토픽에는 `failReason` 필드가 포함된 `CouponIssueRetryEventDto`가 실립니다.

### StuckProcessingRecoveryScheduler

PROCESSING 상태로 진행이 멈춘 레코드를 자동으로 감지하고 복구합니다.

| 항목 | 값 |
|------|----|
| 실행 주기 | 60초 (fixedDelay) |
| 감지 기준 | updatedAt < 현재 - 5분 이상 PROCESSING |
| 복구 방식 | DB 상태를 REQUESTED로 리셋 후 원래 토픽으로 재발행 |

```
60초마다 실행
       │
       ▼
  findStuckWithCampaign(PROCESSING, now - 5분)
       │  JOIN FETCH로 campaign 즉시 로딩
       ▼
  각 stuck 레코드에 대해
       ├── resetToRequested(requestId)  ─→ DB: REQUESTED
       └── republishToOriginalTopic()   ─→ Kafka: 캠페인 타입에 맞는 원본 토픽
```

---

## 설계 패턴

### Transactional Outbox Pattern

- **STEP2**: UserCoupon 저장과 OutboxEvent(PENDING)를 **같은 트랜잭션**으로 저장
  → Kafka 발행 전 장애가 나도 데이터 유실 없음
- **OutboxScheduler (5초 주기)**: PENDING 이벤트를 `coupon-issue-complete` 토픽으로 발행 → PUBLISHED
- **OutboxScheduler (30초 주기)**: FAILED 이벤트 재시도
- STEP4 Kafka 발행이 실패해도 OutboxScheduler가 at-least-once 전달 보장

### Redis Lua 선착순 제어

- Reactive Redis(Mono/Flux)로 non-blocking 수량 제어
- Lua 스크립트로 **SISMEMBER + INCR + 수량 비교**를 원자적으로 실행
  → 분산 환경에서 중복 발급과 overselling 동시 방지

### Optimistic Locking

- `UserCoupon.version` 필드로 동일 쿠폰 중복 사용 방지

---

### CouponEventLog 처리 이력 추적

요청 1건에 대한 전체 처리 이력을 `coupon_event_log` 테이블에서 시계열로 조회할 수 있습니다.

| 처리 시점 | processingStatus | 설명 |
|-----------|-----------------|------|
| 발급 요청 수신 | `PROGRESS` | 최초 이벤트 로그 생성 |
| 재시도 발생 | `RETRYING` | 재시도마다 새 레코드 INSERT (errorMessage 포함) |
| 처리 성공 | `SUCCESS` | 성공 시 새 레코드 INSERT |
| 최종 실패 | `FAILED` | retryCount ≥ 3 소진 시 새 레코드 INSERT |

```sql
-- 요청 1건의 전체 처리 이력 조회
SELECT * FROM coupon_event_log
WHERE request_id = ?
ORDER BY created_at ASC;
```

---

## 도메인 모델

```
CouponCampaign (캠페인)
    └── UserCoupon (발급된 쿠폰)

CouponIssueRequest (발급 요청)
    └── CouponEventLog[] (@ManyToOne — 시도마다 새 레코드 누적)

OutboxEvent (Outbox 이벤트)
```

> `CouponEventLog`는 요청 1건당 처리 시도(최초/재시도/완료/실패)마다 새 레코드를 INSERT하여
> 전체 처리 이력을 보존합니다. 기존 레코드를 UPDATE하지 않습니다.

### IssueRequestStatus 상태 흐름

```
REQUESTED → PROCESSING → ISSUED
                       ↘ FAILED_BUSINESS   (중복 발급 등 비즈니스 거부)
                       ↘ RETRYING          (재시도 진행 중)
                       ↘ FAILED_FATAL      (retryCount >= 3 또는 복구 불가)
```

---

## API 명세

각 서비스의 Swagger UI에서 확인할 수 있습니다.

| 서비스 | Swagger URL |
|--------|-------------|
| coupon-api | http://localhost:8081/swagger-ui.html |
| coupon-consumer | http://localhost:8082/swagger-ui.html |
| coupon-admin | http://localhost:8083/swagger-ui.html |
| product-stub | http://localhost:8084/swagger-ui.html |

### 쿠폰 발급 API

```
POST /api/issue
```

```json
{
  "userId": "user1",
  "campaignId": 1
}
```

### 어드민 API (coupon-admin)

**캠페인 관리**

```
GET    /admin/campaigns?page=0&size=20            - 전체 캠페인 목록 (페이지네이션)
POST   /admin/campaigns                           - 캠페인 생성
GET    /admin/campaigns/{id}                      - 캠페인 상세
PATCH  /admin/campaigns/{id}/status               - 캠페인 상태 변경 (ACTIVE / INACTIVE / ENDED)
```

**발급 요청 모니터링**

```
GET    /admin/issue-requests?status=ISSUED&page=0&size=20   - 발급 요청 목록 (status 필터 + 페이지네이션)
GET    /admin/issue-requests/{id}                           - 발급 요청 상세
```

**유저 쿠폰 모니터링**

```
GET    /admin/user-coupons?userId=user1&page=0&size=20      - 유저 쿠폰 목록 (userId 필터 + 페이지네이션)
```

**페이지네이션 응답 구조** (Spring `Page<T>`)

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

---

## DB 마이그레이션

### CouponEventLog OneToMany 전환 (신규 설치는 불필요)

기존 DB에서 운영 중이었다면 `coupon_event_log.request_id`의 UNIQUE 제약을 제거해야 합니다.

```sql
-- 인덱스 이름은 실제 DB에서 확인 후 수정
ALTER TABLE coupon_event_log DROP INDEX UK_request_id;
```

`schema.sql` 기준 신규 설치는 UNIQUE 제약이 없으므로 별도 작업 불필요.

---

## 인프라 설정

`docker-compose.yml`로 로컬 인프라를 한 번에 실행할 수 있습니다.

```bash
docker-compose up -d
```

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis | 6380 |
| Kafka | 9092 |
| Zookeeper | 2181 |

## 실행 방법

```bash
# 1. 인프라 실행
docker-compose up -d

# 2. 전체 빌드
./gradlew build

# 3. 각 모듈 실행
./gradlew :coupon-api:bootRun
./gradlew :coupon-consumer:bootRun
./gradlew :coupon-admin:bootRun
```

## DB 접속 정보 (로컬)

| 항목 | 값 |
|------|-----|
| Host | localhost:3306 |
| Database | coupon |
| Username | abcd |
| Password | 1234 |
