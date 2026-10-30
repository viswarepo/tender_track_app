package com.tender.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    /**
     * Events are keyed by tenant id, so one tenant's events stay in order within a partition.
     */
    @Bean
    NewTopic tenderEvents() {
        return TopicBuilder.name(TaskEvent.TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * Retry a failing event 3 times, one second apart. Add a dead-letter topic before production so nothing is dropped.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
    }
}
