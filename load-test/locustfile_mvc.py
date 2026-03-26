from locust import HttpUser, task, between, events
from requests.exceptions import ConnectionError, Timeout
import random
import string
from collections import Counter

# ───────────────────────────────────────────────
# 설정
# ───────────────────────────────────────────────
API_HOST = "http://host.docker.internal:8084"  # MVC v1 서버

# 테스트할 캠페인 ID (data.sql 기준)
FIRST_COME_COUPON_ID = 1   # 선착순 10,000개

# 응답 코드 집계용
result_counter = Counter()


def random_user_id():
    """매 요청마다 고유 유저 ID 생성 (중복 발급 방지 시나리오)"""
    return "mvc_user_" + "".join(random.choices(string.ascii_lowercase + string.digits, k=8))


# ───────────────────────────────────────────────
# 시나리오 1: 선착순 쿠폰 발급 (핵심 부하 테스트)
# ───────────────────────────────────────────────
class FirstComeCouponUser(HttpUser):
    """
    선착순 쿠폰 발급 시나리오 (MVC 블로킹)
    - 다수의 유저가 동시에 동일한 선착순 쿠폰(couponId=1) 발급 요청
    - Spring MVC + Tomcat 스레드 풀 성능 측정
    """
    host = API_HOST
    wait_time = between(0.1, 0.5)

    def on_start(self):
        """각 가상 유저 시작 시 고유 userId 할당"""
        self.user_id = random_user_id()

    @task
    def issue_first_come_coupon(self):
        payload = {
            "couponId": FIRST_COME_COUPON_ID,
            "userId": self.user_id
        }
        try:
            with self.client.post(
                "/api/issue",
                json=payload,
                headers={"Content-Type": "application/json"},
                name="/api/issue [선착순-MVC]",
                catch_response=True,
                timeout=5
            ) as response:
                if response.status_code == 200:
                    result_counter["success"] += 1
                    response.success()
                elif response.status_code == 400:
                    # 재고 소진 또는 중복 발급 → GlobalExceptionHandler가 400으로 반환
                    result_counter["sold_out_or_duplicate"] += 1
                    response.success()
                elif response.status_code == 0:
                    result_counter["connection_error"] += 1
                    response.failure("Connection dropped (server overloaded)")
                else:
                    result_counter["error"] += 1
                    response.failure(f"Unexpected status: {response.status_code} - {response.text[:100]}")
        except (ConnectionError, Timeout):
            result_counter["connection_error"] += 1


# ───────────────────────────────────────────────
# 시나리오 2: 동일 유저 중복 발급 시도
# ───────────────────────────────────────────────
class DuplicateIssueUser(HttpUser):
    """
    동일 유저가 같은 쿠폰을 반복 요청하는 시나리오
    - Redis 중복 차단 로직 검증
    """
    host = API_HOST
    wait_time = between(0.05, 0.2)

    def on_start(self):
        # 소수의 고정 유저 ID 사용 → 중복 발급 시도 유발
        self.user_id = f"mvc_dup_user_{random.randint(1, 10)}"

    @task
    def duplicate_issue(self):
        payload = {
            "couponId": FIRST_COME_COUPON_ID,
            "userId": self.user_id
        }
        try:
            with self.client.post(
                "/api/issue",
                json=payload,
                headers={"Content-Type": "application/json"},
                name="/api/issue [중복시도-MVC]",
                catch_response=True,
                timeout=5
            ) as response:
                # 200(성공) 또는 400(중복/재고소진) 모두 서버 정상
                if 0 < response.status_code < 500:
                    response.success()
                else:
                    response.failure(f"Server error: {response.status_code}")
        except (ConnectionError, Timeout):
            pass


# ───────────────────────────────────────────────
# 테스트 종료 시 결과 출력
# ───────────────────────────────────────────────
@events.quitting.add_listener
def on_quit(environment, **kwargs):
    print("\n========== [MVC v1] 발급 결과 집계 ==========")
    print(f"  성공 (SUCCESS):             {result_counter['success']}")
    print(f"  재고소진/중복 (SOLD_OUT):   {result_counter['sold_out_or_duplicate']}")
    print(f"  연결오류 (CONNECTION):      {result_counter['connection_error']}")
    print(f"  오류 (ERROR):               {result_counter['error']}")
    print("=============================================\n")
