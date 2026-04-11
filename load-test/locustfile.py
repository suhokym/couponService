from locust import HttpUser, task, between, events
from requests.exceptions import ConnectionError, Timeout
import random
import string
import json
from collections import Counter

# ───────────────────────────────────────────────
# 설정
# ───────────────────────────────────────────────
API_HOST = "http://host.docker.internal:8081"

# 테스트할 캠페인 ID (data.sql 기준)
FIRST_COME_COUPON_ID = 1   # 선착순 10,000개

# 응답 코드 집계용
result_counter = Counter()


def random_user_id():
    """매 요청마다 고유 유저 ID 생성 (중복 발급 방지 시나리오)"""
    return "user_" + "".join(random.choices(string.ascii_lowercase + string.digits, k=8))


# ───────────────────────────────────────────────
# 시나리오 1: 선착순 쿠폰 발급 (핵심 부하 테스트)
# ───────────────────────────────────────────────
class FirstComeCouponUser(HttpUser):
    """
    선착순 쿠폰 발급 시나리오
    - 다수의 유저가 동시에 동일한 선착순 쿠폰(couponId=1) 발급 요청
    - Redis 수량 제어 + Kafka 비동기 처리 성능 측정
    """
    host = API_HOST
    wait_time = between(0.1, 0.5)  # 요청 간 대기 (초)

    def on_start(self):
        """각 가상 유저 시작 시 고유 userId 할당"""
        self.user_id = random_user_id()

    @task
    def issue_first_come_coupon(self):
        """선착순 쿠폰 발급 요청 (비중 높음)"""
        payload = {
            "couponId": FIRST_COME_COUPON_ID,
            "userId": random_user_id()  # ← 매 요청마다 새 유저
        }
        try:
            with self.client.post(
                "/api/issue",
                json=payload,
                headers={"Content-Type": "application/json"},
                name="/api/issue [선착순]",
                catch_response=True,
                timeout=5  # 5초 타임아웃
            ) as response:
                if response.status_code == 200:
                    result_counter["success"] += 1
                    response.success()
                elif response.status_code == 204:
                    # 재고 소진 또는 중복 발급 → 비즈니스 실패지만 서버는 정상
                    result_counter["sold_out_or_duplicate"] += 1
                    response.success()
                elif response.status_code == 0:
                    # 연결 자체가 끊김 → 서버 과부하 신호
                    result_counter["connection_error"] += 1
                    response.failure("Connection dropped (server overloaded)")
                else:
                    result_counter["error"] += 1
                    response.failure(f"Unexpected status: {response.status_code} - {response.text[:100]}")
        except (ConnectionError, Timeout) as e:
            result_counter["connection_error"] += 1


# ───────────────────────────────────────────────
# 테스트 종료 시 결과 출력
# ───────────────────────────────────────────────
@events.quitting.add_listener
def on_quit(environment, **kwargs):
    print("\n========== 발급 결과 집계 ==========")
    print(f"  성공 (SUCCESS):             {result_counter['success']}")
    print(f"  재고소진/중복 (SOLD_OUT):   {result_counter['sold_out_or_duplicate']}")
    print(f"  연결오류 (CONNECTION):      {result_counter['connection_error']}")
    print(f"  오류 (ERROR):               {result_counter['error']}")
    print("=====================================\n")
