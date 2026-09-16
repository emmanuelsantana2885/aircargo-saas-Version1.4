package com.aircargo.warehouseservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Productor AMQP del warehouse-service.
 * Publica receipt.created en el TopicExchange compartido aircargo.events.
 * NO declara colas: los consumidores (notification, booking, mawb) las declaran
 * con sus propios bindings sobre el mismo exchange.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "aircargo.events";
    public static final String RECEIPT_CREATED_KEY = "receipt.created";

    @Bean
    public TopicExchange aircargoEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}