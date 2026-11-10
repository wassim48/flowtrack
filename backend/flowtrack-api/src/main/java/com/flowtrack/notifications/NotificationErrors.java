package com.flowtrack.notifications;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class NotificationErrors {
    @Bean
    DefaultErrorHandler notificationErrorHandler(KafkaTemplate<String,String> producer) {
        var recoverer = new DeadLetterPublishingRecoverer(producer,
                (record,error) -> new TopicPartition(record.topic()+".dlt",-1));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer,new FixedBackOff(1000,3));
    }
}
