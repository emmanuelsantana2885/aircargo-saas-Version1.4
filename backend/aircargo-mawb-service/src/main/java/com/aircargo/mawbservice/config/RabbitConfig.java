package com.aircargo.mawbservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String EXCHANGE = "aircargo.events";
    public static final String RECEIPT_SYNC_QUEUE = "aircargo.mawb.receipt-status";
    public static final String RECEIPT_CREATED_KEY = "receipt.created";

    @Bean
    public TopicExchange aircargoEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue mawbReceiptSyncQueue() {
        return new Queue(RECEIPT_SYNC_QUEUE, true);
    }

    @Bean
    public Binding receiptSyncBinding(Queue mawbReceiptSyncQueue, TopicExchange aircargoEventsExchange) {
        return BindingBuilder.bind(mawbReceiptSyncQueue).to(aircargoEventsExchange).with(RECEIPT_CREATED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}