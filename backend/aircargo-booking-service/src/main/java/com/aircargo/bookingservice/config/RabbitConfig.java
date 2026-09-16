package com.aircargo.bookingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AMQP del booking-service:
 * - Productor: publica booking.awb.updated (confirmación de AWB).
 * - Consumidor: cola aircargo.booking.receipt-sync sobre receipt.created —
 *   reemplaza el sync Feign síncrono que el warehouse hacía hacia bookings.
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String EXCHANGE = "aircargo.events";
    public static final String AWB_UPDATED_KEY = "booking.awb.updated";
    public static final String RECEIPT_SYNC_QUEUE = "aircargo.booking.receipt-sync";
    public static final String RECEIPT_CREATED_KEY = "receipt.created";

    @Bean
    public TopicExchange aircargoEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue bookingReceiptSyncQueue() {
        return new Queue(RECEIPT_SYNC_QUEUE, true);
    }

    @Bean
    public Binding receiptSyncBinding(Queue bookingReceiptSyncQueue, TopicExchange aircargoEventsExchange) {
        return BindingBuilder.bind(bookingReceiptSyncQueue).to(aircargoEventsExchange).with(RECEIPT_CREATED_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}