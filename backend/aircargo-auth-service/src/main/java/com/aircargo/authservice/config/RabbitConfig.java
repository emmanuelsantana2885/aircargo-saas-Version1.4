package com.aircargo.authservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología AMQP del consumidor de auditoría de auth-service (Fase 1).
 *
 * auth escribe su propia auditoría local (AuditService propio + AuditEventStore);
 * este consumidor persiste en {@code auth.audit_log} los eventos de auditoría que
 * publican los DEMÁS servicios (routing key {@code audit.log}). El acceso a la
 * tabla compartida lo garantiza {@literal ?currentSchema=auth,flight,public} del
 * datasource (el usuario aircargo_user es owner/root de la BD).
 *
 * Cola durable con DLQ: tras 3 reintentos (1s, 2s, 4s) el mensaje que sigue
 * fallando va a la DLQ para inspección/reproceso, nunca se re-encola infinito.
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitConfig {

    static final String EXCHANGE = "aircargo.events";
    static final String QUEUE_AUDIT_LOG = "aircargo.auth.audit-log";
    static final String DLX = "aircargo.dlx";
    static final String QUEUE_AUDIT_DLQ = "aircargo.auth.audit-log.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /** Cola de trabajo de auditoría: durable + dead-letter hacia aircargo.dlx. */
    @Bean
    public Queue auditLogQueue() {
        return QueueBuilder.durable(QUEUE_AUDIT_LOG)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE_AUDIT_DLQ)
                .build();
    }

    /** DLQ: retiene eventos que fallaron tras los reintentos para inspección/reproceso. */
    @Bean
    public Queue auditLogDlq() {
        return QueueBuilder.durable(QUEUE_AUDIT_DLQ).build();
    }

    @Bean
    public DirectExchange dlx() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Binding dlqBinding(Queue auditLogDlq, DirectExchange dlx) {
        return BindingBuilder.bind(auditLogDlq).to(dlx).with(QUEUE_AUDIT_DLQ);
    }

    @Bean
    public Binding auditLogBinding(Queue auditLogQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(auditLogQueue).to(eventsExchange).with("audit.log");
    }

    /** Los productores publican JSON (Jackson2JsonMessageConverter) — consumo igual. */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Factory con reintentos: 3 intentos (1s, 2s, 4s); el fallo persistente va a la DLQ. */
    @Bean
    public SimpleRabbitListenerContainerFactory retryListenerFactory(ConnectionFactory connectionFactory,
                                                                     Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}