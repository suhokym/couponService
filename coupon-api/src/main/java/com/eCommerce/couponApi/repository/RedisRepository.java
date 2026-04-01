package com.eCommerce.couponApi.repository;

import com.eCommerce.couponApi.repository.redisDto.CouponIssueRequestCode;
import com.eCommerce.couponDomain.exception.CouponIssueException;
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

     private RedisScript<String> FirstissueScript = checkFirstIssueScript();
     private RedisScript<String> OpenIssueScript = checkOpenIssueScript();


    public void removeMember(Long key, String member) {redisTemplate.opsForSet().remove(getIssuedCouponUsers(key), member);}
    // 캐시 조회
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 캐시 저장
    public Mono<Boolean> set(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    // 분산 락 획득 (NX: 키 없을 때만 설정)
    public Mono<Boolean> tryLock(String lockKey, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
    }

    // 분산 락 해제
    public Mono<Long> unlock(String lockKey) {
        return redisTemplate.delete(lockKey);
    }

    public Mono<CouponIssueRequestCode> issueRequest(long couponId, String userId, Integer totalIssueQuantity){
        String IssueCouponKey = getIssuedCouponUsers(couponId);

        if (totalIssueQuantity == null){
            return redisTemplate.execute(
                            OpenIssueScript,
                            List.of(IssueCouponKey),
                            String.valueOf(userId)

                    )
                    .next()
                    .map(CouponIssueRequestCode::findCode)
                    .doOnNext(CouponIssueRequestCode::checkRequestResult)
                    .onErrorMap(e -> e instanceof CouponIssueException ? e : new RuntimeException("OPEN COUPON Redis 실행 오류: " + e.getMessage()));
        }

            return redisTemplate.execute(
                            FirstissueScript,
                    List.of(IssueCouponKey),
                    String.valueOf(userId),
                    String.valueOf(totalIssueQuantity)

            )
                    .next()
                    .map(CouponIssueRequestCode::findCode)
                    .doOnNext(CouponIssueRequestCode::checkRequestResult)
                    .onErrorMap(e -> e instanceof CouponIssueException ? e : new RuntimeException("FIRST COUPON Redis 실행 오류: " + e.getMessage()));

    }



    private RedisScript<String> checkFirstIssueScript(){
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

    private RedisScript<String> checkOpenIssueScript(){
        // 중복 체크
        String script = """
                if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return '2'
                end
                
                redis.call('SADD', KEYS[1], ARGV[1]) return '4'               
   
                """;
        return RedisScript.of(script, String.class);
    }



}
