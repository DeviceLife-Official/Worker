package com.devicelife.devicelife_worker.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsConfig {

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        SqsMessageListenerContainerFactory<Object> factory = new SqsMessageListenerContainerFactory<>();
        factory.setSqsAsyncClient(sqsAsyncClient);

        // 📝 새로운 컨버터 설정
        SqsMessagingMessageConverter converter = new SqsMessagingMessageConverter();

        // 🔥 [핵심] 어떤 JavaType 헤더가 오더라도 무시하고 String 클래스로 매핑하도록 강제 설정
        // 이렇게 하면 'api.scheduler.JobMessage'가 적혀 있어도 무시하고 String으로 변환합니다.
        converter.setPayloadTypeMapper(message -> String.class);

        factory.configure(options -> options.messageConverter(converter));
        return factory;
    }
}