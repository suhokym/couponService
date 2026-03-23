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

## 주요 기능

### 쿠폰 캠페인 유형
| 유형 | 설명 |
|------|------|
| `FIRST_COME` | 선착순 수량 제한 발급 |
| `CODE` | 쿠폰 코드 기반 발급 |
| `OPEN` | 수량 무제한 발급 |
| `MANUAL` | 관리자 직접 발급 |

### 비동기 발급 플로우

```
Client → coupon-api (WebFlux) → Redis (수량 선점) → Kafka → coupon-consumer → MySQL (실제 발급)
```

1. **API 수신**: 발급 요청을 즉시 수락 후 응답 반환
2. **Redis 제어**: 선착순 캠페인의 경우 Redis로 분산 수량 제어
3. **Kafka 발행**: 발급 요청 이벤트를 Kafka 토픽으로 발행
4. **Consumer 처리**: 실제 `UserCoupon` 생성 및 상태 업데이트

## 설계 패턴

### Transactional Outbox Pattern
- Kafka 발행과 DB 저장을 같은 트랜잭션으로 묶어 신뢰성 보장
- 스케줄러가 `outbox_event` 테이블의 PENDING 이벤트를 Kafka로 재발행
- 최소 1회 전달(at-least-once) 보장

### Optimistic Locking
- `UserCoupon.version` 필드로 동일 쿠폰 중복 사용 방지

### Redis 기반 선착순 제어
- Reactive Redis(Mono/Flux)로 non-blocking 수량 제어
- 분산 환경에서 overselling 방지

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
  "userId": 1,
  "campaignId": 1
}
```

## 도메인 모델

```
CouponCampaign (캠페인)
    └── UserCoupon (발급된 쿠폰)

CouponIssueRequest (발급 요청)
    └── CouponEventLog (이벤트 처리 로그)

OutboxEvent (Outbox 이벤트)
```

### IssueRequestStatus 상태 흐름

```
REQUESTED → PROCESSING → ISSUED
                       ↘ FAILED_BUSINESS
                       ↘ FAILED_RETRYABLE → RETRYING → ...
                       ↘ FAILED_FATAL
```

## DB 접속 정보 (로컬)

| 항목 | 값 |
|------|-----|
| Host | localhost:3306 |
| Database | coupon |
| Username | abcd |
| Password | 1234 |
