CREATE TABLE `coupon_campaign` (
  `campaign_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '캠페인 고유 식별자',
  `name` varchar(255) NOT NULL COMMENT '캠페인 이름 (예: 여름 시즌 선착순 쿠폰)',
  `total_quantity` int NOT NULL COMMENT '총 발급 가능 수량',
  `status` ENUM ('ACTIVE', 'INACTIVE', 'ENDED') NOT NULL COMMENT '캠페인 상태',
  `start_at` datetime NOT NULL COMMENT '캠페인 시작일시',
  `end_at` datetime NOT NULL COMMENT '캠페인 종료일시',
  `created_at` datetime NOT NULL COMMENT '생성일자',
  `updated_at` datetime NOT NULL COMMENT '수정일자'
);

CREATE TABLE `coupon_issue_request` (
  `request_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '발급 요청 고유 식별자',
  `user_id` bigint NOT NULL COMMENT '요청 유저 ID',
  `campaign_id` bigint NOT NULL COMMENT '캠페인 FK',
  `status` ENUM ('REQUESTED', 'PROCESSING', 'ISSUED', 'FAILED_BUSINESS', 'FAILED_RETRYABLE', 'FAILED_FATAL', 'RETRYING') NOT NULL COMMENT '요청 처리 상태',
  `fail_reason` varchar(255) COMMENT '실패 사유 (실패 시에만 기록)',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
  `created_at` datetime NOT NULL COMMENT '요청 일자',
  `updated_at` datetime NOT NULL COMMENT '수정 일자'
);

CREATE TABLE `user_coupon` (
  `user_coupon_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '유저 쿠폰 고유 식별자',
  `user_id` bigint NOT NULL COMMENT '소유 유저 ID',
  `campaign_id` bigint NOT NULL COMMENT '캠페인 FK',
  `coupon_code` varchar(255) UNIQUE NOT NULL COMMENT '발급된 고유 쿠폰 코드',
  `status` ENUM ('ISSUED', 'USED', 'EXPIRED', 'RESERVED') NOT NULL COMMENT '쿠폰 사용 상태',
  `reserved_order_id` bigint COMMENT '선점된 주문 ID (결제 연동 시 사용)',
  `reserved_at` datetime COMMENT '선점 일시',
  `used_at` datetime COMMENT '사용 일시',
  `expired_at` datetime NOT NULL COMMENT '만료 일시',
  `version` int NOT NULL DEFAULT 0 COMMENT '낙관적 락용 버전',
  `created_at` datetime NOT NULL COMMENT '발급 일자',
  `updated_at` datetime NOT NULL COMMENT '수정 일자'
);

CREATE TABLE `outbox_event` (
  `outbox_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '아웃박스 고유 식별자',
  `aggregate_type` varchar(255) NOT NULL COMMENT '이벤트 발생 도메인 (예: COUPON_ISSUE)',
  `aggregate_id` bigint NOT NULL COMMENT '도메인 엔티티 ID (예: user_coupon.user_coupon_id)',
  `event_type` varchar(255) NOT NULL COMMENT '이벤트 유형 (예: COUPON_ISSUED, COUPON_EXPIRED)',
  `payload` json NOT NULL COMMENT 'Kafka로 발행할 이벤트 데이터',
  `publish_status` ENUM ('PENDING', 'PUBLISHED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '발행 상태',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
  `created_at` datetime NOT NULL COMMENT '적재 일자',
  `updated_at` datetime NOT NULL COMMENT '수정 일자'
);

CREATE TABLE `coupon_event_log` (
  `event_id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '이벤트 로그 고유 식별자',
  `request_id` bigint NOT NULL COMMENT '발급 요청 FK',
  `event_type` varchar(255) NOT NULL COMMENT '이벤트 유형 (예: ISSUE_REQUESTED, ISSUE_COMPLETED)',
  `payload` json COMMENT '이벤트 처리 당시 데이터',
  `processing_status` ENUM ('SUCCESS', 'FAILED', 'RETRYING') NOT NULL COMMENT '처리 상태',
  `error_message` varchar(255) COMMENT '오류 메시지 (실패 시에만 기록)',
  `created_at` datetime NOT NULL COMMENT '로그 생성 일자'
);

ALTER TABLE `coupon_issue_request` ADD FOREIGN KEY (`campaign_id`) REFERENCES `coupon_campaign` (`campaign_id`);

ALTER TABLE `user_coupon` ADD FOREIGN KEY (`campaign_id`) REFERENCES `coupon_campaign` (`campaign_id`);

ALTER TABLE `coupon_event_log` ADD FOREIGN KEY (`request_id`) REFERENCES `coupon_issue_request` (`request_id`);
