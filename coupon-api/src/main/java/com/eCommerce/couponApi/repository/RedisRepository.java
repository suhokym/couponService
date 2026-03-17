package com.eCommerce.couponApi.repository;

import com.eCommerce.couponApi.repository.redisDto.CouponIssueReqeustCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static com.eCommerce.couponApi.util.CouponRedisUtil.getIssuedCouponUsers;

@RequiredArgsConstructor
@Repository
public class RedisRepository {


    private final ReactiveRedisTemplate<String, String> redisTemplate;

     private RedisScript<String> issueScript = checkIssueScript();

    public Mono<Long> sAdd(String key, String value){return redisTemplate.opsForSet().add(key, value);}

    public Mono<Long> sCard(String key){return redisTemplate.opsForSet().size(key);}

    public Mono<Boolean> sIsMember(String key, String value){return redisTemplate.opsForSet().isMember(key, value);}

    // 캐시 조회
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 캐시 저장
    public Mono<Boolean> set(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Mono<CouponIssueReqeustCode> issueRequest(long couponId, String userId, int totalIssueQuantity){
        String IssueCouponKey = getIssuedCouponUsers(couponId);

            return redisTemplate.execute(
                    issueScript,
                    List.of(IssueCouponKey),
                    String.valueOf(userId),
                    String.valueOf(totalIssueQuantity)

            )
                    .next()
                    .map(CouponIssueReqeustCode::findCode)
                    .doOnNext(CouponIssueReqeustCode::checkRequestResult)
                    .onErrorMap(e ->new RuntimeException("Redis 실행 오류: " + e.getMessage()));

    }

    private RedisScript<String> checkIssueScript(){
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
