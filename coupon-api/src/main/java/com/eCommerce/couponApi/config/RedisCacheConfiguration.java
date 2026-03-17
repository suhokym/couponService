package com.eCommerce.couponApi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@RequiredArgsConstructor
@Configuration
public class RedisCacheConfiguration {

    private final RedisConnectionFactory redisConnectionFactory;

    @Primary
    @Bean
    public CacheManager cacheManager() {
        org.springframework.data.redis.cache.RedisCacheConfiguration redisCacheConfiguration = org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext
                        .SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext
                        .SerializationPair
                        .fromSerializer(RedisSerializer.java()))
                .entryTtl(Duration.ofMinutes(20));

        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }


}
