package com.eCommerce.couponConsumer.config;

import com.eCommerce.couponDomain.dto.CouponIssueRetryEventDto;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {


    @Bean
    public KafkaListenerContainerFactory<?> retryContainerFactory(
            KafkaProperties kafkaProperties) {
        // ⚠️ NOTE: retry 토픽은 CouponIssueRetryEventDto(failReason 포함)를 사용하므로
        //          메인 토픽용 default factory와 분리하여 타입 불일치를 방지한다.
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssueRetryEventDto.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        DefaultKafkaConsumerFactory<String, CouponIssueRetryEventDto> cf =
                new DefaultKafkaConsumerFactory<>(props);
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueRetryEventDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setBatchListener(true);
        return factory;
    }

}
