package com.eCommerce.couponApiMvc.repository;

import com.eCommerce.couponApiMvc.repository.redisDto.CouponIssueRequestCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

import static com.eCommerce.couponApiMvc.util.CouponRedisUtil.getIssuedCouponUsers;

@RequiredArgsConstructor
@Repository
public class RedisRepository {

    private final StringRedisTemplate redisTemplate;

    // ⚠️ NOTE: WebFlux 버전과 동일한 Lua 스크립트 재사용 — 원자성 보장
    private final RedisScript<String> issueScript = checkIssueScript();

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // 분산 락 획득 (NX: 키 없을 때만 설정)
    public Boolean tryLock(String lockKey, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
    }

    // 분산 락 해제
    public void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    public CouponIssueRequestCode issueRequest(long couponId, String userId, int totalIssueQuantity) {
        String issueCouponKey = getIssuedCouponUsers(couponId);
        String result = redisTemplate.execute(
                issueScript,
                List.of(issueCouponKey),
                userId,
                String.valueOf(totalIssueQuantity)
        );
        if (result == null) {
            throw new RuntimeException("Redis 스크립트 실행 결과가 null입니다.");
        }
        return CouponIssueRequestCode.findCode(result);
    }

    private RedisScript<String> checkIssueScript() {
        // 중복 체크, 최대 발급수 체크
        String script = """
                if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return '2'
                end

                if tonumber(ARGV[2]) > redis.call('SCARD', KEYS[1]) then redis.call('SADD', KEYS[1], ARGV[1]) return '1'
                end

                return '3'
                """;
        return RedisScript.of(script, String.class);
    }
}
