INSERT IGNORE INTO coupon_campaign (coupon_id, name, total_quantity, issued_quantity, type, status, start_at, end_at, created_at, updated_at)
VALUES
(1, '봄맞이 선착순 10% 할인 쿠폰', 10000, 0, 'FIRST_COME', 'ACTIVE', '2026-03-01 00:00:00', '2026-06-30 23:59:59', NOW(), NOW()),
(2, '신규가입 선착순 5000원 할인 쿠폰', 50000, 0, 'FIRST_COME', 'ACTIVE', '2026-03-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(3, 'VIP 전용 20% 할인 코드 쿠폰', 5000, 0, 'CODE', 'ACTIVE', '2026-03-01 00:00:00', '2026-06-30 23:59:59', NOW(), NOW()),
(4, '제휴사 프로모션 코드 쿠폰', 3000, 0, 'CODE', 'ACTIVE', '2026-04-01 00:00:00', '2026-09-30 23:59:59', NOW(), NOW()),
(5, '전회원 무료배송 쿠폰', NULL, NULL, 'OPEN', 'ACTIVE', '2026-03-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(6, '앱 다운로드 감사 쿠폰', NULL, NULL, 'OPEN', 'ACTIVE', '2026-03-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(7, 'CS 보상 쿠폰', 1000, 0, 'MANUAL', 'ACTIVE', '2026-01-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(8, '이벤트 당첨자 쿠폰', 500, 0, 'MANUAL', 'ACTIVE', '2026-03-15 00:00:00', '2026-06-30 23:59:59', NOW(), NOW());